(ns blaze.elm.compiler.reusing-logic-test
  (:require
    [blaze.anomaly :as ba]
    [blaze.elm.compiler :as c]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.test-util :as tu]
    [blaze.elm.literal-spec]
    [blaze.elm.quantity :as quantity]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(st/instrument)
(tu/instrument-compile)


(defn- fixture [f]
  (st/instrument)
  (tu/instrument-compile)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; 9.2. ExpressionRef
;;
;; The ExpressionRef type defines an expression that references a previously
;; defined NamedExpression. The result of evaluating an ExpressionReference is
;; the result of evaluating the referenced NamedExpression.
(deftest compile-expression-ref-test
  (testing "Throws error on missing expression"
    (given (ba/try-anomaly (c/compile {} #elm/expression-ref "name-170312"))
      ::anom/category := ::anom/incorrect
      ::anom/message := "Expression definition `name-170312` not found."
      :context := {}))

  (testing "Result Type"
    (let [library {:statements {:def [{:name "name-170312" :resultTypeName "result-type-name-173029"}]}}
          expr (c/compile {:library library} #elm/expression-ref "name-170312")]
      (is (= "result-type-name-173029" (:result-type-name (meta expr))))))

  (testing "Eval"
    (let [library {:statements {:def [{:name "name-170312"}]}}
          expr (c/compile {:library library} #elm/expression-ref "name-170312")]
      (is (= ::result (core/-eval expr {:library-context {"name-170312" ::result}} nil nil))))))


;; 9.4. FunctionRef
(deftest compile-function-ref-test
  (testing "ToString"
    (are [elm res]
      (= res (core/-eval (c/compile {} elm) {} nil nil))
      {:type "FunctionRef"
       :libraryName "FHIRHelpers"
       :name "ToString"
       :operand [#elm/string "foo"]}
      "foo"))

  (testing "ToQuantity"
    (let [context {:library {:parameters {:def [{:name "x"}]}}}
          elm {:type "FunctionRef"
               :libraryName "FHIRHelpers"
               :name "ToQuantity"
               :operand [#elm/parameter-ref"x"]}
          expr (c/compile context elm)]
      (are [x res] (= res (core/-eval expr {:parameters {"x" x}} nil nil))
        {:value 23M :code "kg"} (quantity/quantity 23M "kg")
        {:value 42M} (quantity/quantity 42M "1")
        {} nil))))
