(ns lh.utils-test
  (:require [clojure.test :refer :all]
            [lh.utils :as sut]))

(deftest test-contains-in?
  (is (true? (sut/contains-in? {:a {:b {:c {:d 1}}}} [:a])))
  (is (true? (sut/contains-in? {:a {:b {:c {:d 1}}}} [:a :b])))
  (is (true? (sut/contains-in? {:a {:b {:c {:d 1}}}} [:a :b :c])))
  (is (true? (sut/contains-in? {:a {:b {:c {:d 1}}}} [:a :b :c :d])))
  (is (false? (sut/contains-in? {:a {:b {:c {:d 1}}}} [:a :d]))))

(deftest deep-merge
  (are [a b expected] (is (= expected (sut/deep-merge a b)))
    ;; base cases
    {} {} {}
    nil nil nil
    ;; child is nil = parent value
    {} nil {}
    {:a 1} nil {:a 1}
    ;; child value takes precedence
    {:a 1} {:a 2} {:a 2}
    {:a 1} {:a 2} {:a 2}
    {:a 1} {:b 2} {:a 1 :b 2}
    ;; nil is different from undefined
    {:a 1} {:a nil} {:a nil}
    {:a 1} {} {:a 1}
    ;; nil isn't discarded from parent
    {:a nil} {} {:a nil}
    {:a nil} {:a 1} {:a 1}
    ;; check proper vector nesting
    {:a [1 {:b 2} 3]} {:a [4 {:c 5}]} {:a [1 {:b 2} 3 4 {:c 5}]}
    ;; check multiple level nesting
    {:super {:nested [{:example {:structure ["old"]}}]}}
    {:super {:nested [{:example {:structure ["new"]}}]}}
    {:super {:nested [{:example {:structure ["old"]}} {:example {:structure ["new"]}}]}}
    ;; replace meta
    {:data {:a 1 :b 2}} {:data ^:replace {:a 420}} {:data {:a 420}}
    ;; differing types
    {:a 1} :test :test
    [1] :test :test
    :test {:a 1} {:a 1}
    :test [1] [1]))

(deftest test-deep-merge-presevers-metadata
  (testing "map merging"
    (let [left (with-meta {:a 1 :b 2} {:my-meta 42 :boolean? false})
          right (with-meta {:a 3 :b 4} {:boolean? true})]
      (is (= {:my-meta 42 :boolean? true} (meta (sut/deep-merge left right))))))
  (testing "vector merging"
    (let [left (with-meta [1 2 3] {:my-meta 42 :boolean? false})
          right (with-meta [4 5 6] {:boolean? true})]
      (is (= {:my-meta 42 :boolean? true} (meta (sut/deep-merge left right)))))))

(deftest test-keyword-path
  (is (= [:a :b :c] (sut/keyword-path :a.b.c))))

(deftest test-wrap-scalar
  (is (= [1] (sut/wrap-scalar 1)))
  (is (= [1] (sut/wrap-scalar [1]))))

(deftest test-namespaced-with?
  (is (true? (sut/namespaced-with? "def" :def/hi)))
  (is (false? (sut/namespaced-with? "foo" :def/hi)))
  (is (false? (sut/namespaced-with? "foo" 'def/hi))))
