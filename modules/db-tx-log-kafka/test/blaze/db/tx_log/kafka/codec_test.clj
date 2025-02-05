(ns blaze.db.tx-log.kafka.codec-test
  (:require
    [blaze.db.tx-log.kafka.codec :as codec]
    [blaze.db.tx-log.spec]
    [blaze.test-util :as tu :refer [satisfies-prop]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [clojure.test.check.properties :as p]))


(set! *warn-on-reflection* true)
(st/instrument)


(test/use-fixtures :each tu/fixture)


(defn- serialize [tx-cmds]
  (.serialize codec/serializer "topic-093717" tx-cmds))


(defn- deserialize [data]
  (.deserialize codec/deserializer "topic-094321" data))


(deftest round-trip-test
  (satisfies-prop 100
    (p/for-all [tx-cmds (s/gen :blaze.db/tx-cmds)]
      (= tx-cmds (deserialize (serialize tx-cmds))))))


(defn- invalid-cbor-content
  "`0xA1` is the start of a map with one entry."
  []
  (byte-array [0xA1]))


(deftest deserializer-test
  (testing "empty value"
    (is (nil? (deserialize (byte-array 0)))))

  (testing "invalid cbor value"
    (is (nil? (deserialize (invalid-cbor-content)))))

  (testing "invalid map value"
    (is (nil? (deserialize (serialize {:a 1}))))))
