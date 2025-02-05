(ns blaze.interaction.search-type
  "FHIR search interaction.

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.anomaly :refer [if-ok when-ok]]
    [blaze.async.comp :as ac :refer [do-sync]]
    [blaze.db.api :as d]
    [blaze.db.spec]
    [blaze.fhir.spec.type :as type]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.search.include :as include]
    [blaze.interaction.search.nav :as nav]
    [blaze.interaction.search.params :as params]
    [blaze.interaction.search.util :as search-util]
    [blaze.interaction.util :as iu]
    [blaze.page-store :as page-store]
    [blaze.page-store.spec]
    [blaze.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- type-list [db type page-id]
  (if page-id
    (d/type-list db type page-id)
    (d/type-list db type)))


(defn- type-query [db type clauses page-id]
  (if page-id
    (d/type-query db type clauses page-id)
    (d/type-query db type clauses)))


(defn- execute-query [db query page-id]
  (if page-id
    (d/execute-query db query page-id)
    (d/execute-query db query)))


(defn- handles-and-clauses
  [{:keys [type] :blaze.preference/keys [handling]
    {:keys [clauses page-id]} :params}
   db]
  (cond
    (empty? clauses)
    {:handles (type-list db type page-id)}

    (identical? :blaze.preference.handling/strict handling)
    (when-ok [handles (type-query db type clauses page-id)]
      {:handles handles
       :clauses clauses})

    :else
    (when-ok [query (d/compile-type-query-lenient db type clauses)]
      {:handles (execute-query db query page-id)
       :clauses (d/query-clauses query)})))


(defn- build-matches-only-page [page-size handles]
  (let [handles (into [] (take (inc page-size)) handles)]
    (if (< page-size (count handles))
      {:matches (pop handles)
       :next-match (peek handles)}
      {:matches handles})))


(defn- build-page [db include-defs page-size handles]
  (if (:direct include-defs)
    (let [handles (into [] (take (inc page-size)) handles)]
      (if (< page-size (count handles))
        (let [page-handles (pop handles)]
          {:matches page-handles
           :includes (include/add-includes db include-defs page-handles)
           :next-match (peek handles)})
        {:matches handles
         :includes (include/add-includes db include-defs handles)}))
    (build-matches-only-page page-size handles)))


(defn- entries [context match-futures include-futures]
  (log/trace "build entries")
  (-> (into
        []
        (comp (mapcat ac/join)
              (map (partial search-util/entry context)))
        match-futures)
      (into
        (comp (mapcat ac/join)
              (map #(search-util/entry context % search-util/include)))
        include-futures)))


(defn- pull-matches-fn [{:keys [elements]}]
  (if (seq elements)
    #(d/pull-many %1 %2 elements)
    d/pull-many))


(defn- page-data
  "Returns a CompletableFuture that will complete with a map of:

  :entries - the bundle entries of the page
  :num-matches - the number of search matches (excluding includes)
  :next-handle - the resource handle of the first resource of the next page
  :clauses - the actually used clauses

  or an anomaly in case of errors."
  {:arglists '([context])}
  [{:blaze/keys [db] {:keys [include-defs page-size] :as params} :params :as context}]
  (if-ok [{:keys [handles clauses]} (handles-and-clauses context db)]
    (let [{:keys [matches includes next-match]}
          (build-page db include-defs page-size handles)
          match-futures (mapv (partial (pull-matches-fn params) db) (partition-all 100 matches))
          include-futures (mapv (partial d/pull-many db) (partition-all 100 includes))]
      (-> (ac/all-of (into match-futures include-futures))
          (ac/exceptionally
            #(assoc %
               ::anom/category ::anom/fault
               :fhir/issue "incomplete"))
          (ac/then-apply
            (fn [_]
              {:entries (entries context match-futures include-futures)
               :num-matches (count matches)
               :next-handle next-match
               :clauses clauses}))))
    ac/completed-future))


(defn- self-link-offset [first-entry]
  (when-let [id (-> first-entry :resource :id)]
    {"__page-id" id}))


(defn- link [relation url]
  {:fhir/type :fhir.Bundle/link
   :relation relation
   :url url})


(defn- self-link [{:keys [self-link-url-fn]} clauses first-entry]
  (link "self" (self-link-url-fn clauses (self-link-offset first-entry))))


(defn- first-link [{:keys [first-link-url-fn]} token clauses]
  (link "first" (first-link-url-fn token clauses)))


(defn- next-link-offset [next-handle]
  {"__page-id" (:id next-handle)})


(defn- next-link
  [{:keys [next-link-url-fn]} token clauses next-handle]
  (let [url (next-link-url-fn token clauses (next-link-offset next-handle))]
    (link "next" url)))


(defn- total
  "Calculates the total number of resources returned.

  If we have no clauses (returning all resources), we can use `d/type-total`.
  Secondly, if the number of entries found is not more than one page in size,
  we can use that number. Otherwise, there is no cheap way to calculate the
  number of matching resources, so we don't report it."
  [{:keys [type] :blaze/keys [db] {:keys [clauses page-id]} :params}
   num-matches next-handle]
  (cond
    ;; evaluate this criteria first, because we can potentially safe the
    ;; d/type-total call
    (and (nil? page-id) (nil? next-handle))
    num-matches

    (empty? clauses)
    (d/type-total db type)))


(defn- zero-bundle
  "Generate a special bundle if the search results in zero matches to avoid
  generating a token for the first link, we don't need in this case."
  [context clauses entries]
  {:fhir/type :fhir/Bundle
   :id (iu/luid context)
   :type #fhir/code"searchset"
   :total #fhir/unsignedInt 0
   :link [(self-link context clauses (first entries))]})


(defn- normal-bundle [context token clauses entries total]
  (cond->
    {:fhir/type :fhir/Bundle
     :id (iu/luid context)
     :type #fhir/code"searchset"
     :entry entries
     :link [(self-link context clauses (first entries))
            (first-link context token clauses)]}

    total
    (assoc :total (type/->UnsignedInt total))))


(defn- gen-token! [{{:keys [token]} :params :keys [gen-token-fn]} clauses]
  (if token
    (ac/completed-future token)
    (gen-token-fn clauses)))


(defn- search-normal [context]
  (-> (page-data context)
      (ac/then-compose
        (fn [{:keys [entries num-matches next-handle clauses]}]
          (let [total (total context num-matches next-handle)]
            (if (some-> total zero?)
              (ac/completed-future (zero-bundle context clauses entries))
              (do-sync [token (gen-token! context clauses)]
                (if next-handle
                  (-> (normal-bundle context token clauses entries total)
                      (update :link conj (next-link context token clauses next-handle)))
                  (normal-bundle context token clauses entries total)))))))))


(defn- compile-type-query
  [{:keys [type] :blaze/keys [db] :blaze.preference/keys [handling]
    {:keys [clauses]} :params}]
  (if (identical? :blaze.preference.handling/strict handling)
    (d/compile-type-query db type clauses)
    (d/compile-type-query-lenient db type clauses)))


(defn- summary-total
  [{:keys [type] :blaze/keys [db] {:keys [clauses]} :params :as context}]
  (if (empty? clauses)
    (ac/completed-future {:total (d/type-total db type)})
    (if-ok [query (compile-type-query context)]
      (do-sync [total (d/count-query db query)]
        {:total total
         :clauses (d/query-clauses query)})
      ac/completed-future)))


(defn- search-summary [context]
  (do-sync [{:keys [total clauses]} (summary-total context)]
    {:fhir/type :fhir/Bundle
     :id (iu/luid context)
     :type #fhir/code"searchset"
     :total (type/->UnsignedInt total)
     :link [(self-link context clauses [])]}))


(defn- search [{:keys [params] :as context}]
  (if (:summary? params)
    (search-summary context)
    (search-normal context)))


(defn- match
  [{{{:fhir.resource/keys [type]} :data} ::reitit/match
    ::reitit/keys [router]}
   name]
  (reitit/match-by-name router (keyword type name)))


(defn- self-link-url-fn [{:blaze/keys [base-url db] :as request} params]
  (fn [clauses offset]
    (nav/url base-url (match request "type") params clauses (d/t db) offset)))


(defn- gen-token-fn
  [{:keys [page-store]} {{{route-name :name} :data} ::reitit/match}]
  (if (= "search" (some-> route-name name))
    (fn [clauses]
      (if (empty? clauses)
        (ac/completed-future nil)
        (page-store/put! page-store clauses)))
    (fn [_clauses]
      (ac/completed-future nil))))


(defn- first-link-url-fn
  "Returns a function of `token` and `clauses` that returns the URL of the first
  link."
  [{:blaze/keys [base-url db] :as request} params]
  (fn [token clauses]
    (nav/token-url base-url (match request "page") params token clauses
                   (d/t db) nil)))


(defn- next-link-url-fn
  "Returns a function of `token`, `clauses` and `offset` that returns the URL
  of the next link."
  [{:blaze/keys [base-url db] :as request} params]
  (fn [token clauses offset]
    (nav/token-url base-url (match request "page") params token clauses
                   (d/t db) offset)))


(defn- search-context
  [{:keys [page-store] :as context}
   {{{:fhir.resource/keys [type]} :data} ::reitit/match
    :keys [headers params]
    :blaze/keys [base-url db]
    ::reitit/keys [router]
    :as request}]
  (let [handling (handler-util/preference headers "handling")]
    (do-sync [params (params/decode page-store handling params)]
      (cond->
        (assoc context
          :blaze/base-url base-url
          :blaze/db db
          ::reitit/router router
          :type type
          :params params
          :self-link-url-fn (self-link-url-fn request params)
          :gen-token-fn (gen-token-fn context request)
          :first-link-url-fn (first-link-url-fn request params)
          :next-link-url-fn (next-link-url-fn request params))
        handling
        (assoc :blaze.preference/handling handling)))))


(defmethod ig/pre-init-spec :blaze.interaction/search-type [_]
  (s/keys :req-un [:blaze/clock :blaze/rng-fn :blaze/page-store]))


(defmethod ig/init-key :blaze.interaction/search-type [_ context]
  (log/info "Init FHIR search-type interaction handler")
  (fn [request]
    (-> (search-context context request)
        (ac/then-compose search)
        (ac/then-apply ring/response))))
