(ns blaze.scheduler-test
  (:require
    [blaze.scheduler :as sched]
    [blaze.scheduler-spec]
    [blaze.test-util :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [java-time :as time]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest schedule-at-fixed-rate-test
  (with-system [{:blaze/keys [scheduler]} {:blaze/scheduler {}}]
    (let [state (atom 0)]
      (sched/schedule-at-fixed-rate scheduler #(swap! state inc)
                                    (time/millis 100) (time/millis 100))

      (testing "the function wasn't called yet"
        (is (zero? @state)))

      (Thread/sleep 120)

      (testing "the function was called once"
        (is (= 1 @state)))

      (Thread/sleep 100)

      (testing "the function was called twice"
        (is (= 2 @state))))))


(deftest cancel-test
  (with-system [{:blaze/keys [scheduler]} {:blaze/scheduler {}}]
    (let [future (sched/schedule-at-fixed-rate scheduler identity
                                               (time/millis 100)
                                               (time/millis 100))]

      (is (sched/cancel future false)))))


(deftest shutdown-timeout-test
  (let [{:blaze/keys [scheduler] :as system} (ig/init {:blaze/scheduler {}})]

    ;; will produce a timeout, because the function runs 11 seconds
    (sched/schedule-at-fixed-rate scheduler #(Thread/sleep 11000)
                                  (time/millis 0) (time/millis 100))

    ;; ensure that the function is called before the scheduler is halted
    (Thread/sleep 100)

    (ig/halt! system)

    ;; the scheduler is shut down
    (is (.isShutdown scheduler))

    ;; but it isn't terminated yet
    (is (not (.isTerminated scheduler)))))
