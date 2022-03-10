(ns lh.library-test
  (:require [clojure.test :refer :all]
            [lh.library :as sut]
            [lh.test-support :refer [lh] :as test]))
;;
;; Stage 1 tests
;;

(deftest test-expand-in-keys
  (is (= {:deeply {:nested 1}}
         (sut/expand-in-keys lh {:in/deeply.nested 1}))))

(deftest test-expand-no-parents!
  (let [expected (test/node :. :test :no-parents)]
    (is (= expected (sut/expand! lh expected)))))

(deftest test-expand-with-parents!
  (let [base-a (test/node :. :lib :a)
        test-a (test/node :. :test :lib :a)
        base-b (test/node :. :lib :b)
        test-b (test/node :. :test :lib :b)
        base-c (test/node :. :lib :c)
        with-parents (test/node :. :test :with-parents)]
    (is (= (test/tree with-parents [base-a
                                    test-a
                                    base-b
                                    (test/tree test-b [base-c])])
           (sut/expand! lh with-parents)))))

(deftest test-expand-direct-resolve-parents
  (let [base-a (test/node :. :lib :a)
        base-c (test/node :. :lib :c)
        direct-parents (test/node :. :test :direct-parents)]
    (is (= (test/tree direct-parents [base-a base-c]) (sut/expand! lh direct-parents)))))

;;
;; Stage 2 tests
;;
(deftest test-dir-meta
  (testing "metadata-prefix == dir"
    (is (= {:services {:kafka "kafka"
                       :database "database"
                       :redis "base-redis"}
            :database {:port 1234}}
           (sut/dir-meta lh "")))
    (is (= {:services {:redis "test-redis"}
            :database {:host "my-host"}}
           (sut/dir-meta lh "test"))))
  (testing "metadata-prefix == dir"
    (let [lh (assoc lh :metadata-prefix "_")]
      (is (= {} (sut/dir-meta lh "")))
      (is (= {:special "special-value"
              :more-special {:super "special"}}
             (sut/dir-meta lh "test"))))))

(deftest test-split-meta!
  (let [with-parents (test/node :. :test :with-parents)]
    (is (= [{:_file (:def/_file with-parents)
             :_basename "with-parents"
             :from ["a" "b"]
             :my-meta "abc"}
            {:with-parents true}]
           (sut/split-meta! lh with-parents)))))

(deftest test-tree-nodes
  (let [base-a (test/node :. :lib :a)
        test-a (test/node :. :test :lib :a)
        base-b (test/node :. :lib :b)
        test-b (test/node :. :test :lib :b)
        base-c (test/node :. :lib :c)
        with-parents (test/node :. :test :with-parents)
        files #(map :def/_file %)]
    (is (= (files [base-a test-a base-b base-c test-b with-parents])
           (files (sut/tree-nodes lh (sut/expand! lh with-parents)))))))

(deftest test-merge-meta
  (let [direct-parents (test/node :. :test :direct-parents)
        [metadata merged-manifest] (sut/merge-tree! lh (sut/expand! lh direct-parents))]
    (is (= {:_basename "direct-parents"
            :_file (:def/_file direct-parents)
            :dir "test"
            :services {:kafka "kafka"
                       :database "database"
                       :redis "test-redis"}
            :database {:host "my-host" :port 1234}} metadata))
    (is (= {:base-a true
            :base-c true
            :direct-parents true
            :my-value 420} merged-manifest))))

;;
;; Stage 3 tests
;;

(deftest test-replace-refs!
  (let [meta {:name "darin"
              :env "dev"
              :namespace :ref/env}]
    (testing "missing references throws errors"
      (is (thrown? clojure.lang.ExceptionInfo #"unresolved meta reference"
                   (sut/replace-refs! lh meta {:my-data :ref/doesnt-exist}))))
    (testing "cyclic references throw"
      (is (thrown? clojure.lang.ExceptionInfo #"cyclic-reference"
                   (sut/replace-refs! lh {:def/a :ref/b :def/b :ref/a} {:my-data :ref/a}))))
    (testing "missing env throws"
      (is (thrown? clojure.lang.ExceptionInfo #"unresolved env reference"
                   (sut/replace-refs! lh meta {:my-data :env/does-not-exist})))
      (is (thrown? clojure.lang.ExceptionInfo #"unresolved env reference"
                   (sut/replace-refs! lh meta {:my-data :env/does-not-exist}))))
    (testing "env is substituted"
      (is (some? (:path (sut/replace-refs! lh {} {:path :env/PATH})))))
    (testing "resolves all meta references properly"
      (is (= {:my-name "darin" :where "dev"}
             (sut/replace-refs! lh meta {:my-name :ref/name :where :ref/namespace}))))))

(deftest test-apply-fns!
  (testing "lists are called as fns"
    (is (= {:sum 15} (sut/apply-fns! {} {} {:sum '(+ 1 2 3 4 5)})))
    (testing "errors get propagated"
      (is (thrown? clojure.lang.ExceptionInfo
                   (sut/apply-fns! {} {} {:bad-stuff '(/ 1 0)}))))))

;;
;; Stage 5 tests
;;

(deftest test-camelCase
  (are [input expected] (= expected (sut/camelCase input))
    ;; base cases
    nil nil
    {} {}
    {:a 1} {"a" 1}
    ;; kebab-case and snake_case are both handled
    {:hello-world 1 :how_are :you} {"helloWorld" 1 "howAre" :you}
    ;; conversion happens recursively
    {:nested-maps {:are-neat [1 2 3 {:a-bee-ceee 3}]}} {"nestedMaps" {"areNeat" [1 2 3 {"aBeeCeee" 3}]}}
    ;; symbols skip casing
    {'symbols-are :skipped} {"symbols-are" :skipped}
    {'my-ns/symbols-are :skipped} {"my-ns/symbols-are" :skipped}))
