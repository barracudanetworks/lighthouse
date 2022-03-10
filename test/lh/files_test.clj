(ns lh.files-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [lh.files :as sut]
            [lh.test-support :as test]
            [lh.utils :as utils]))

(deftest test-name-fns
  (let [file1 (io/file "/tmp/a.edn")
        file2 (io/file "/tmp/b")]
    (testing "basename"
      (is (= "a" (sut/basename file1)))
      (is (= "b" (sut/basename file2))))
    (testing "filename"
      (is (= "a.edn" (sut/filename file1)))
      (is (= "b" (sut/filename file2))))
    (testing "dirname"
      (is (= "tmp" (sut/dirname file1)))
      (is (= "tmp" (sut/dirname file2))))
    (testing "extension"
      (is (= :edn (sut/extension file1)))
      (is (nil? (sut/extension file2))))))

(deftest test-list
  (test/with-tmp-files [d (.getCanonicalFile (io/file "/tmp" "test"))
                        file1 (io/file d "a.edn")
                        file2 (io/file d "b.edn")]
    ;; setup some files
    (.mkdirs d)
    (.createNewFile file1)
    (.createNewFile file2)
    ;; test 'em
    (testing "simple cases"
      (is (empty? (sut/file-list {:root "/tmp"} "does-not-exist")))
      (is (every? #{file1 file2} (sut/file-list {:root "/tmp"} "test"))))
    (testing "does not recur down"
      (test/with-tmp-files [d2 (io/file d "inner-test")
                            file3 (io/file d2 "inner.edn")]
        (.mkdirs d2)
        (.createNewFile file3)
        (is (every? #{file1 file2} (sut/file-list {:root "/tmp"} "test")))
        (is (every? #{file3} (sut/file-list d2)))))))

(deftest test-read-edn
  (test/with-tmp-files [d (io/file "/tmp" "test")
                        edn (io/file d "test.edn")]
    (let [data {:def/test 123
                :value '(str :ref/test)}]
      (spit edn (utils/prettify :edn data))
      (is (= (assoc data
                    :def/_file edn
                    :def/_basename "test")
             (sut/read-edn test/lh edn))))))
