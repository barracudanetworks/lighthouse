(ns lh.utils
  (:require [clj-yaml.core :as yaml]
            [clojure.data.json :as json]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [clojure.walk :as walk]))

(defn contains-in?
  "Returns true if `path` exists in `config`."
  [map [first & rest]]
  (cond
    ;; If we're given a bad  or used up all the path
    (or (nil? map) (not (seqable? map))) false
    ;; Return true if this is the last part of the path
    (and (empty? rest) (contains? map first)) true
    :else (contains-in? (get map first) rest)))

(defn deep-merge
  "Deep mergeds `child` into `parent`.

  nil leaf values will not override truthy values."
  [parent child]
  (cond
    ;; Allow for `^:replace` to be used to completely replace chunks
    ;; of manifest.
    (:replace (meta child))
    child

    (and (map? parent) (map? child))
    (let [obj-meta (deep-merge (meta parent) (meta child))]
      (with-meta
        (reduce-kv (fn [acc k v]
                     (let [parent-val (get acc k)]
                       (assoc acc k (when (some? v)
                                      (deep-merge parent-val v)))))
                   parent
                   child)
        obj-meta))

    (and (vector? parent) (vector? child))
    (let [obj-meta (deep-merge (meta parent) (meta child))]
      (with-meta (into [] (concat parent child)) obj-meta))

    :else (if (some? child) child parent)))

(defn keyword-path [value]
  (mapv keyword
        (some-> value
                (name)
                (str/split #"\."))))

(defn wrap-scalar
  [value]
  (if-not (sequential? value)
    [value]
    value))

(defn namespaced-with?
  [ns val]
  (and (or (symbol? val)
           (keyword? val))
       (= (name ns) (namespace val))))

(defn special-key
  [{:keys [namespaces]} ns key]
  (keyword (namespaces (keyword ns)) (name key)))

(defn special-key?
  [{:keys [namespaces]} key]
  (some #(namespaced-with? % key) (vals namespaces)))

(defn from-key
  [lh]
  (special-key lh :def :from))

(defn has-parent-libs?
  "Returns true iff `from` is non-empty."
  [lh lib]
  (->> lh
       (from-key)
       (lib)
       (not-empty)
       (boolean)))

(defn sorted-maps
  "Recursively sorts all maps in the provided data"
  [data]
  (letfn [(resolver [node]
            (cond
              (map? node)
              (into (sorted-map) node)

              :else node))]
    (walk/postwalk resolver data)))

(defn prettify
  "Pretty-prints data into several different formats."
  [type data]
  (case type
    :edn (with-out-str (pprint/pprint data))
    :json (with-out-str (json/pprint data))
    :yaml (yaml/generate-string data :dumper-options {:flow-style :block})))

(defn log*
  [line]
  (println line))

(defn log
  [{:keys [verbose]} line]
  (when verbose
    (log* line)))

(defn logf
  [lh & format-args]
  (log lh (apply format format-args)))
