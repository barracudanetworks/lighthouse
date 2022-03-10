(ns lh.processors.kube-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [lh.config :as config]
            [lh.library :as lib]
            [lh.processors.kube :as sut]))

(def lh (-> (io/resource "test/no-parents.edn")
            (io/file)
            (config/load)
            (assoc :processor :kube)))

(deftest test-all-paths
  (let [input {:a {:b {:c 1 :d [1 2 3]}}
               :one [1 2 3]}
        output [[:a]
                [:a :b]
                [:a :b :c]
                [:a :b :d]
                [:one]]]
    (is (= output (#'sut/all-paths input)))))

(deftest test-->env-name
  (is (= "A" (#'sut/->env-name lh [:a])))
  (is (= "A__B" (#'sut/->env-name lh [:a :b])))
  (is (= "MULTI_WORD__PARAMETER_PATH" (#'sut/->env-name lh [:multi-word :parameter-path]))))

(deftest test-value-from
  (testing "value-from env values"
    (testing "secret references"
      (is (= {:value-from {:secret-key-ref {:name "foo" :key "bar"}}} (#'sut/value-from :secret.foo/bar))))
    (testing "config references"
      (is (= {:value-from {:config-map-key-ref {:name "foo" :key "bar"}}} (#'sut/value-from :config.foo/bar))))
    (testing "field references"
      (is (= {:value-from {:field-ref {:api-version "v1" :field-path "foo.bar"}}}
             (#'sut/value-from :field.foo/bar))))
    (testing "unknown references"
      (is (= {:value :some/value} (#'sut/value-from :some/value))))))

(deftest test-env-value
  (is (= {:name "A" :value "1"} (#'sut/env-value lh [:a] 1)))
  (is (= {:name "A__B" :value "42"} (#'sut/env-value lh [:a :b] 42)))
  (is (= {:name "MULTI_WORD__PATH" :value ":hello"} (#'sut/env-value lh [:multi-word :path] :hello))))

(deftest test-env-vars
  (let [input {:a 1
               :b {:c :test
                   :d [1 2 3]}
               :e "hello world"
               :f :secret.foo/bar}
        output [{:name "A" :value "1"}
                {:name "B__C" :value ":test"}
                {:name "B__D" :value "[1 2 3]"}
                {:name "E" :value "hello world"}
                {:name "F" :value-from {:secret-key-ref {:name "foo" :key "bar"}}}]]
    (is (= output (#'sut/env-vars lh input)))))

(deftest test-map->named-vector
  (let [input {:a {:container-port 1}
               :b {:mount-path "/here"}
               :c {:image "abc:123"}}
        output [{:name :a :container-port 1}
                {:name :b :mount-path "/here"}
               {:name :c :image "abc:123"}]]
    (is (= output (#'sut/map->named-vector input)))))

(deftest test-resolve-path
  (is (= "registry/some-app:a-version"
         (lib/resolve-path lh
                           {:registry "registry"
                            :tags {:some-app "a-version"}}
                           [:tags :some-app]
                           #{}))))

(deftest test-apply-processor!
  (testing "kube preset converts env and port/volume/etc"
    (let [input {:template {:containers {:main {:env {:db {:name "hiya"
                                                           :password :secret.db/password}
                                                      :admin-port 2020}
                                                :ports {:admin {:container-port 2020}}}}}}
          output {:template {:containers [{:name :main
                                           :env [{:name "ADMIN_PORT" :value "2020"}
                                                 {:name "DB__NAME" :value "hiya"}
                                                 {:name "DB__PASSWORD"
                                                  :value-from {:secret-key-ref {:name "db"
                                                                                :key "password"}}}]
                                           :ports [{:name :admin
                                                    :container-port 2020}]}]}}]
      (is (= output (lib/apply-processor! lh [{} input]))))))
