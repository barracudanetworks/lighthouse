(ns lh.config-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [lh.config :as sut]
            [lh.test-support :as test]))

(deftest test-search-from
  (test/with-tmp-files [outer (io/file "/tmp" "outer")
                        inner (io/file outer "inner")
                        config (io/file inner "lighthouse.edn")
                        inner2 (io/file inner "inner2")
                        inner3 (io/file inner2 "inner3")
                        inner4 (io/file inner3 "inner4")]
    (is (nil? (sut/search-from outer)))
    (is (= [config ["."]] (sut/search-from inner)))
    (is (= [config ["." "inner2" "inner3" "inner4"]] (sut/search-from inner4)))))

(deftest test-load
  (testing "no config found"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo #"no config file found"
         (sut/load nil))))
  (testing "relative file paths"
    (let [current-dir (io/file ".")]
      (test/with-tmp-files [lh-config (io/file "lighthouse.edn")
                            file1 (io/file "file1.edn")
                            inner (io/file "inner")
                            file2 (io/file inner "file2.edn")]
        (spit lh-config (pr-str {}))
        (is (= {:dir "",
                :paths [["."]],
                :root (.getCanonicalPath current-dir)}
               (select-keys (sut/load file1) [:dir :paths :root])))
        (is (= {:dir "inner",
                :paths [["."] ["." "inner"]],
                :root (.getCanonicalPath current-dir)}
               (select-keys (sut/load inner) [:dir :paths :root])))
        (is (= {:dir "inner",
                :paths [["."] ["." "inner"]],
                :root (.getCanonicalPath current-dir)}
               (select-keys (sut/load file2) [:dir :paths :root]))))))
  (test/with-tmp-files [tmp (io/file "/tmp")
                        app-config (io/file tmp "lighthouse.edn")
                        file1 (io/file tmp "file1.edn")
                        inner (io/file tmp "inner")
                        file2 (io/file inner "file2.edn")]
    (spit app-config (pr-str {}))
    (testing "missing required-keys"
      (with-redefs [sut/required-keys [:a]]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"missing required key"
                              (sut/load app-config)))))
    (testing "config values"
      (let [expected {:root "/tmp"
                      ;; calculated configs
                      :dir ""
                      :paths [["."]]
                      :def/_basename "lighthouse"
                      :def/_file app-config
                      ;; defaults
                      :output-format :edn
                      :output-mode :file
                      :verbose false
                      :processor nil
                      :library-prefix "lib/"
                      :metadata-prefix "meta/"
                      :output-prefix "manifests/"
                      :namespaces
                      {:def "def"
                       :ref "ref"
                       :env "env"
                       :in "in"
                       :group "group"}
                      :processors {:kube {:nest-str "__"
                                          :kebab-str "_"
                                          :tags {:namespace "tags"
                                                 :registry-key :registry
                                                 :tags-key :tags}}}}]
        (is (= expected (dissoc (sut/load file1) :references))))
      (testing "overrides"
        (spit app-config (pr-str {:library-prefix "my-lib/"
                                  :metadata-prefix "my-meta/"
                                  :output-prefix "my-manifests/"
                                  :processor :kube
                                  :output-format :json}))
        (let [expected {:root "/tmp"
                        ;; calculated configs
                        :dir "inner"
                        :paths [["."]
                                ["." "inner"]]
                        :def/_basename "lighthouse"
                        :def/_file app-config
                        ;; overrides
                        :output-format :json
                        :output-mode :file
                        :verbose false
                        :processor :kube
                        :library-prefix "my-lib/"
                        :metadata-prefix "my-meta/"
                        :output-prefix "my-manifests/"
                        ;; defaults
                        :namespaces
                        {:def "def"
                         :ref "ref"
                         :env "env"
                         :in "in"
                         :group "group"}
                        :processors {:kube {:nest-str "__"
                                            :kebab-str "_"
                                            :tags {:namespace "tags"
                                                   :registry-key :registry
                                                   :tags-key :tags}}}}]
          (is (= expected
                 (dissoc (sut/load file2) :references)
                 (dissoc (sut/load inner) :references))))))))
