(ns blaze.elm.util-test
  (:require
    [blaze.elm.util :as elm-util]
    [blaze.elm.util-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest parse-qualified-name-test
  (testing "nil"
    (is (nil? (elm-util/parse-qualified-name nil))))

  (testing "empty string"
    (is (nil? (elm-util/parse-qualified-name ""))))

  (testing "invalid string"
    (are [s] (nil? (elm-util/parse-qualified-name s))
      "a"
      "aa"))

  (testing "valid string"
    (are [s ns name] (= [ns name] (elm-util/parse-qualified-name s))
      "{a}b" "a" "b")))


(deftest parse-type-test
  (testing "ELM type"
    (is (= "String" (elm-util/parse-type {:type "NamedTypeSpecifier" :name "{urn:hl7-org:elm-types:r1}String"}))))

  (testing "list type"
    (is (= "List<Encounter>" (elm-util/parse-type {:type "ListTypeSpecifier" :elementType {:type "NamedTypeSpecifier" :name "{http://hl7.org/fhir}Encounter"}})))))
