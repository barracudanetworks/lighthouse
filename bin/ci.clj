(ns ci
  (:require [clojure.string :as string]))

(defn get-changelog-entry
  [release]
  (->> (string/split-lines (slurp "CHANGELOG.md"))
       (drop-while #(not (string/starts-with? % (str "## " release))))
       (rest)
       (take-while #(not (string/starts-with? % "##")))
       (string/join "\n")
       (string/trim)
       (println)))

(defn check-version
  [check-version]
  (let [resources-version (string/trim (slurp "resources/VERSION"))]
    (if (= check-version resources-version)
      (System/exit 0)
      (do
        (println (format (str "version does not match resources/VERSION!\n\n"
                              "  checked version: '%s'\n"
                              "resources/VERSION: '%s'")
                         check-version
                         resources-version))
        (System/exit 1)))))
