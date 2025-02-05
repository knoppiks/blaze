(ns blaze.page-store.weigh-test
  (:require
    [blaze.page-store.weigh :as w]
    [clojure.test :refer [are deftest testing]]
    [cuerdas.core :as c-str]))


(deftest weigh-test
  (testing "String"
    (are [s size] (= size (w/weigh s))
      "" 40
      "a" 48
      (c-str/repeat "a" 8) 48
      (c-str/repeat "a" 9) 56))

  (testing "Vector"
    (are [v size] (= size (w/weigh v))
      [] 56
      [:x] 64
      [""] 104
      (vec (repeat 2 :x)) 64
      (vec (repeat 3 :x)) 72
      (vec (repeat 3 "")) 192
      (vec (repeat 4 :x)) 72
      (vec (repeat 5 :x)) 80))

  (testing "typical clauses"
    (are [clauses size] (= size (w/weigh clauses))
      [["code" "10509002"]] 224
      [["code" "http://snomed.info/sct|10509002"]] 248)))
