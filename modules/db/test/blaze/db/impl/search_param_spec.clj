(ns blaze.db.impl.search-param-spec
  (:require
    [blaze.db.impl.batch-db.spec]
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.iterators-spec]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param.util-spec]
    [blaze.db.kv-spec]
    [blaze.db.search-param-registry-spec]
    [blaze.fhir-path-spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/def :blaze.db.compartment/c-hash
  :blaze.db/c-hash)


(s/def :blaze.db.compartment/res-id
  bytes?)


(s/def :blaze.db/compartment
  (s/keys :req-un [:blaze.db.compartment/c-hash :blaze.db.compartment/res-id]))


(s/fdef search-param/compile-values
  :args (s/cat :search-param :blaze.db/search-param
               :values (s/coll-of some? :min-count 1))
  :ret (s/coll-of some? :min-count 1))


(s/fdef search-param/resource-handles
  :args (s/cat :search-param :blaze.db/search-param
               :context :blaze.db.impl.batch-db/context
               :tid :blaze.db/tid
               :modifier (s/nilable :blaze.db.search-param/modifier)
               :compiled-values (s/coll-of some? :min-count 1)
               :start-id (s/nilable :blaze.db/id-bytes))
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef search-param/compartment-resource-handles
  :args (s/cat :search-param :blaze.db/search-param
               :context :blaze.db.impl.batch-db/context
               :compartment :blaze.db/compartment
               :tid :blaze.db/tid
               :compiled-values (s/coll-of some? :min-count 1))
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef search-param/matches?
  :args (s/cat :search-param :blaze.db/search-param
               :context :blaze.db.impl.batch-db/context
               :tid :blaze.db/tid
               :id :blaze.db/id-bytes
               :hash :blaze.resource/hash
               :modifier (s/nilable :blaze.db.search-param/modifier)
               :compiled-values (s/coll-of some? :min-count 1))
  :ret boolean?)


(s/fdef search-param/compartment-ids
  :args (s/cat :search-param :blaze.db/search-param
               :resource :blaze/resource)
  :ret (s/coll-of :blaze.resource/id))


(s/fdef search-param/index-entries
  :args (s/cat :search-param :blaze.db/search-param
               :hash :blaze.resource/hash
               :resource :blaze/resource
               :linked-compartments (s/coll-of (s/tuple string? string?)))
  :ret (s/or :entries (s/coll-of :blaze.db.kv/put-entry-w-cf)
             :anomaly ::anom/anomaly))
