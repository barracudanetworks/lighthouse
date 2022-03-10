(ns lh.manifest
  (:refer-clojure :exclude [comment])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [lh.files :as files]
            [lh.library :as lib]
            [lh.utils :as utils])
  (:import [java.io File Writer]))

(set! *warn-on-reflection* true)

;; side-effect: load our processors
(require 'lh.processors.kube)

(defn file-paths-in
  "Returns all unique files in `manifest`.

  'unique files' is defined as either:
    - hitting a library reference (detected by `utils/from-key` existing); or
    - hitting a key with the proper namespace set"
  ([lh manifest]
   (file-paths-in lh manifest []))
  ([{{:keys [group]} :namespaces :as lh} manifest path]
   (mapcat (fn [[key value]]
             (let [new-path (conj path key)]
               (cond
                 (utils/namespaced-with? group key)
                 [new-path]

                 (and (map? value) (value (utils/from-key lh)))
                 [new-path]

                 (map? value)
                 (file-paths-in lh value new-path))))
           manifest)))

(defn- join-path
  [path]
  (when path
    (str/join "." (map name path))))

(defn libraries-under
  "Returns a seq of all library files under `manifest`."
  ([lh manifest]
   (libraries-under lh manifest []))
  ([lh manifest path]
   (cond
     (vector? manifest)
     (mapcat #(libraries-under lh % path) manifest)

     (and (map? manifest) (manifest (utils/from-key lh)))
     [{:group-path (not-empty path)
       :library manifest}]

     (map? manifest)
     (mapcat (fn [[key value]]
               (let [new-path (conj path key)]
                 (libraries-under lh value new-path)))
             manifest))))

(defn split!
  "Splits `manifest` into a map of `{basename [libraries...]}`."
  [lh manifest-file manifest]
  (let [[_meta resolved] (lib/process! lh ::lib/resolve manifest {:throw? false})
        data (into {}
                   (for [path (file-paths-in lh resolved)
                         :let [value (get-in resolved path)
                               libraries (libraries-under lh value)]
                         :when (not-empty libraries)]
                     [(join-path path) libraries]))]
    ;; if this manifest file didn't contain grouped libraries, it's
    ;; most likely just a library file.
    (if (empty? data)
      {(files/basename manifest-file) [{:group-path nil :library manifest}]}
      data)))

(defn process-lib!
  "Process `lib` through `through-stage`."
  [lh through-stage group {:keys [group-path] :as lib}]
  (utils/logf lh "Processing\n%s" (utils/prettify :edn lib))
  (update lib :library #(lib/process! lh through-stage
                                      (assoc %
                                             (utils/special-key lh :def "group") group
                                             (utils/special-key lh :def "group-path") group-path))))

(defn process-all!
  "Applies `through-stage` on all `libraries`."
  ([lh basename libraries]
   (process-all! lh ::lib/prettify basename libraries))
  ([lh through-stage basename libraries]
   (map #(process-lib! lh through-stage basename %) libraries)))

(def comment-char {:edn ";;"
                   :json "//"
                   :yaml "#"})

(defn- comment
  "Makes a comment out of `text` based on `output-format`."
  [{:keys [output-format]} text]
  (str (comment-char output-format) " " text))

(defn- heading
  "Makes a header of `text`."
  [lh & lines]
  (let [lines (remove nil? lines)
        longest (apply max (map count lines))
        separator (-> longest
                      (repeat "-")
                      (str/join))]
    (str/trim
     (with-out-str
       (println (comment lh separator))
       (dorun (map #(println (comment lh %)) lines))
       (println (comment lh separator))))))

(defn- lib-str
  "Generates the final output string for `lib`."
  [lh {:keys [group-path library]}]
  (with-out-str
    (when group-path
      (println (heading lh (join-path group-path))))
    (println library)))

(defn- file-str
  "Generates the final output string for all `libraries`."
  [{:keys [output-format] :as lh} libraries]
  (let [yaml? (= :yaml output-format)
        separator (if yaml?
                    "\n---\n"
                    "\n")]
    (cond->> libraries
      true (map #(lib-str lh %))
      ;; start all yaml files with a separator (edn/json is just an empty space
      ;; so can be avoided)
      yaml? (cons "")
      true (str/join separator)
      true (str (heading lh "AUTO-GENERATED FILE. ANY MANUAL CHANGES WILL BE OVERWRITTEN.")))))

(defn write!
  "Writes output for `libraries` to `basename`."
  [{:keys [output-prefix output-format output-mode paths] :as lh} basename libraries]
  (let [stdout? (= :stdout output-mode)
        output-file (if stdout?
                      *out*
                      (let [^File file (->> output-format
                                            (name)
                                            (format "%s.%s" basename)
                                            (str output-prefix)
                                            ;; `output-prefix` is relative to the inner-most
                                            ;; path (i.e. the file-we're-rendering's directory).
                                            (conj (last paths))
                                            (apply files/resolve lh))]
                        (-> file
                            (.getParentFile)
                            (.mkdirs))
                        file))
        writer ^Writer (io/writer output-file)]
    (.write writer ^String (file-str lh libraries))
    (.flush writer)
    (when-not stdout?
      (.close writer))))
