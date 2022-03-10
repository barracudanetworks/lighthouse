(ns lh.manifest-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [lh.manifest :as sut]
            [lh.test-support :refer [lh] :as test]))

(deftest test-join-path
  (is (= "a.b.c" (#'sut/join-path [:a :b :c]))))

(deftest test-comment
  (is (= ";; foobar" (#'sut/comment lh "foobar")))
  (is (= "// foobar" (#'sut/comment {:output-format :json} "foobar")))
  (is (= "# foobar" (#'sut/comment {:output-format :yaml} "foobar"))))

(deftest test-heading
  (is (= ";; ---\n;; foo\n;; ---" (#'sut/heading lh "foo"))))

(deftest test-file-paths-in
  (is (= #{[:parents :with-parents]
           [:parents :direct-parents]
           [:group/grouped]}
         (into #{} (sut/file-paths-in lh (test/node :. :test :combined))))))

(deftest test-libraries-under
  (let [manifest (test/node :. :test :combined)]
    (is (= #{{:group-path [:with-parents]
              :library {:def/from ["with-parents"]}}
             {:group-path [:direct-parents]
              :library {:def/from ["direct-parents"]}}}
           (into #{} (sut/libraries-under lh (get-in manifest [:group/grouped])))))
    (is (= #{{:group-path nil
              :library {:def/from ["with-parents"]}}}
           (into #{} (sut/libraries-under lh (get-in manifest [:parents :with-parents])))))
    (is (= #{{:group-path nil
              :library {:def/from ["direct-parents"]}}}
           (into #{} (sut/libraries-under lh (get-in manifest [:parents :direct-parents])))))))

(deftest test-split!
  (let [manifest (test/node :. :test :combined)
        plain-lib (test/node :. :test :with-parents)]
    (is (= {"parents.with-parents" [{:group-path nil
                                     :library {:def/from ["with-parents"]}}]
            "parents.direct-parents" [{:group-path nil
                                       :library {:def/from ["direct-parents"]}}]
            "grouped" [{:group-path [:with-parents]
                        :library {:def/from ["with-parents"]}}
                       {:group-path [:direct-parents]
                        :library {:def/from ["direct-parents"]}}]}
           (sut/split! lh (:def/_file manifest) manifest)))
    (is (= {"with-parents" [{:group-path nil :library plain-lib}]}
           (sut/split! lh (:def/_file plain-lib) plain-lib)))))

(deftest test-write!
  (test/with-tmp-files [inner (io/file (:root lh) "inner")
                        inner-manifest (io/file inner "manifests")
                        should-be-empty (io/file inner-manifest "test.yaml")
                        innerest (io/file inner "innerest")
                        innerest-manifest (io/file innerest "manifests")
                        should-have-stuff (io/file innerest-manifest "test.yaml")]
    (let [lh (-> lh
                 (assoc :output-prefix "manifests/"
                        :output-format :yaml
                        :output-mode :file)
                 (update :paths concat [["."] ["." "inner"] ["." "inner" "innerest"]]))]
      (sut/write! lh "test" [])
      (is (pos? (count (slurp should-have-stuff))))
      (is (zero? (count (slurp should-be-empty)))))))
