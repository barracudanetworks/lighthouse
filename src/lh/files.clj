(ns lh.files
  (:refer-clojure :exclude [resolve])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [edamame.core :as e]
            [lh.utils :as utils])
  (:import [java.io File]))

(set! *warn-on-reflection* true)

(defn absolute-file
  [^File file]
  (.getAbsoluteFile file))

(defn exists?
  [^File file]
  (and file (.exists file)))

(defn directory?
  [^File file]
  (.isDirectory file))

(defn filename
  [^File file]
  (.getName file))

(defn basename
  "Returns the basename of a given file."
  [^File file]
  (-> file
      (filename)
      (str/split #"\.")
      (first)))

(defn extension
  "Returns the keyword extension of a give file."
  [^File file]
  (some-> file
          (filename)
          (str/split #"\.")
          (second)
          (keyword)))

(defn dirname
  [^File file]
  (filename
   (if (directory? file)
     file
     (.getParentFile file))))

(defn- post-process
  "Returns a fn that adds location information nodes in an EDN structure.

  Any `special-keys` will be coerced into keywords and location information tracked."
  [{:keys [references] :as lh} ^File f]
  (fn [{:keys [obj loc]}]
    (let [meta (assoc loc :file (.getCanonicalPath f))]
      (cond
        (utils/special-key? lh obj)
        (let [coerced (utils/special-key lh (namespace obj) (name obj))]
          ;; now you may ask:
          ;;   why are you tracking this separately? why not just put it as meta on `obj`?
          ;;
          ;; good question! we're tracking these references separately for a few reasons, here are
          ;; some options that were researched:
          ;;
          ;;   Option 1: Symbols + metadata
          ;;      since our library files can have arbitrary fns anywhere we don't want to introduce a
          ;;      symbol inside a fn which would cause the eval to fail (mainly a problem
          ;;      with manifest files).
          ;;
          ;;   Option 2: defrecords
          ;;      we could just use a defrecord with `[value meta]` and extract the value
          ;;      when we need it. this is doable but makes the tree walking, value replacement, etc
          ;;      quite a bit more complicated
          ;;
          ;;   Option 3: keywords + reference map
          ;;      this is the opion we chose. keywords are simple and doing obfuscate any logic. the
          ;;      biggest downside is the need to store the reference data separately from the keywords
          ;;      (since keywords cannot have metadata). this isn't _much_ of downside however.
          (swap! references assoc coerced meta)
          coerced)

        (instance? clojure.lang.IObj obj)
        (vary-meta obj merge meta)

        :else
        obj))))

(defn read-edn
  [{{:keys [def]} :namespaces :as lh} f & [{:keys [add-meta?] :or {add-meta? true}}]]
  (when (exists? f)
    (let [edn (e/parse-string (slurp f) {:regex true
                                         :fn true
                                         :postprocess (post-process lh f)})]
      (cond-> edn
        (and (map? edn) add-meta?)
        (assoc (utils/special-key lh def "_file") f
               (utils/special-key lh def "_basename") (basename f))))))

(defn resolve
  [{:keys [root]} & path]
  (.getCanonicalFile ^File (apply io/file root (remove str/blank? path))))

(defn file-list
  "Lists all files that are direct children of `dir`."
  ([dir]
   (->> dir
        (file-seq)
        (filter (comp #{:edn} extension))
        (filter #(= dir (.getParentFile ^File %1)))))
  ([lh dir]
   (file-list (resolve lh dir))))
