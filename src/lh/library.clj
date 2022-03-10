(ns lh.library
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [lh.files :as files]
            [lh.utils :as utils]
            [sci.core :as sci]))

(set! *warn-on-reflection* true)

(defn throw-at-node!
  [node msg data]
  (let [data (merge data (meta node))]
    (throw (ex-info (format "%s %s" msg data) data))))

;;
;; Stage 1: Manifest/Tree Expansion
;;
;;   This first stage is all about expansion. The full manifest tree is created
;;   here and any `in` keywords are `assoc-in`'d into their proper spots.
;;

(defn expand-in-keys
  "Expands all `:in/*` keys."
  [{{:keys [in]} :namespaces} manifest]
  (letfn [(expand* [node]
            (let [[key value] (when (vector? node) node)
                  [first & rest] (when (utils/namespaced-with? in key)
                                   (utils/keyword-path key))]
              (if first
                [first (assoc-in {} rest value)]
                node)))]
    (walk/postwalk expand* manifest)))

(defn expand-libs
  "Expands all libs in `manifest`."
  [{:keys [library-prefix paths] :as lh} manifest]
  (when manifest
    (letfn [(read-lib [f]
              (->> f
                   (files/read-edn lh)
                   (expand-in-keys lh)
                   (expand-libs lh)))
            (expand* [name]
              (let [lib (files/resolve lh (str name ".edn"))]
                ;; if this library name is a relative path from the
                ;; root of this lighthouse app, there's no expansion needed.
                (if (files/exists? lib)
                  [(read-lib lib)]
                  ;; otherwise, look for `name` on all `:paths` in the config.
                  (into [] (comp
                            (map (fn [dir-path]
                                   (->> (str library-prefix name ".edn")
                                        (conj dir-path)
                                        (apply files/resolve lh)
                                        (read-lib))))
                            (remove nil?))
                        paths))))]
      (if (utils/has-parent-libs? lh manifest)
        (update manifest (utils/from-key lh) #(mapcat expand* %))
        manifest))))

(defn expand!
  [lh manifest]
  (->> manifest
       (expand-libs lh)
       (expand-in-keys lh)))

;;
;; Stage 2: Tree Merging
;;
;;   Now that we have the full tree, time to combine it all!
;;

;; I don't like `declare`ing, but I'll give in just this once
(declare resolve!)

(defn- strip-prefix
  [{:keys [metadata-prefix]} basename]
  (str/replace basename (re-pattern (str "^" metadata-prefix)) ""))

(defn dir-meta
  [{:keys [metadata-prefix] :as lh} dir]
  (let [f (files/resolve lh dir metadata-prefix)
        meta-files (if (files/directory? f)
                     ;; metadata-prefix points at a directory
                     (files/file-list f)
                     ;; metadata-prefix is simply a filename prefix
                     (->> dir
                          (files/resolve lh)
                          (files/file-list)
                          (filter #(str/starts-with? (files/basename %) metadata-prefix))))]
    (reduce (fn [acc file]
              (let [name (files/basename file)
                    metadata (files/read-edn lh file {:add-meta? false})]
                (->> metadata
                     (hash-map (keyword (strip-prefix lh name)))
                     (expand-in-keys lh)
                     (merge acc))))
            {}
            meta-files)))

(defn merge-meta
  "Deep merges all metas in-order."
  [{:keys [dir paths] :as lh} metas]
  (let [dir-meta (as-> paths $
                      (map #(dir-meta lh (str (apply io/file %))) $)
                      (reduce utils/deep-merge {} $)
                      (assoc $ :dir dir))
        metadata (reduce utils/deep-merge dir-meta metas)]
    (last (resolve! lh
                    [(dissoc metadata :from)
                     (dissoc metadata :from)]
                    {:throw? false}))))

(defn split-meta!
  "Splits out metadata from manifest data."
  [{{:keys [def]} :namespaces} manifest]
  (let [meta-keys (->> manifest
                       (keys)
                       (filter #(utils/namespaced-with? def %)))
        meta (->> meta-keys
                  (select-keys manifest)
                  ;; Strip off the meta namespace as it's
                  ;; redundant now.
                  (map (juxt (comp keyword name key) val))
                  (into {}))]
    [meta (apply dissoc manifest meta-keys)]))

(defn tree-nodes
  "Returns all nodes in `expanded` in proper merge order."
  [lh expanded]
  (if (utils/has-parent-libs? lh expanded)
    (let [parents (into [] (mapcat (partial tree-nodes lh)) (expanded (utils/from-key lh)))]
      (conj parents expanded))
    [expanded]))

(defn merge-tree!
  "Merges all nodes in the `manifest` tree and returns a tuple of `[combined-metadata combined-manifest]`."
  [lh manifest-tree]
  (let [split (map #(split-meta! lh %) (tree-nodes lh manifest-tree))
        metas (map first split)
        manifests (map second split)
        metadata (merge-meta lh metas)
        combined (reduce utils/deep-merge {} manifests)]
    [metadata combined]))

;;
;; Stage 3: Meta/Fn Resolution
;;
;;   This next stage is all about resolving metadata references and fn calls.
;;   This is the first point at which `:processor`s have access to the data.
;;

(defn resolve-path*-dispatch
  [{:keys [processor]} _meta _path _visited]
  processor)
(defmulti resolve-path* #'resolve-path*-dispatch)
(defmethod resolve-path* :default [& _args] nil)

(defn resolve-path
  [{{:keys [ref]} :namespaces :keys [references] :as lh} meta path visited?]
  (or (resolve-path* lh meta path visited?)
      (when (utils/contains-in? meta path)
        (let [val (get-in meta path)]
          (when (and (utils/namespaced-with? ref val) (visited? path))
            (throw-at-node! val
                            "cyclic-reference detected"
                            (assoc (@references meta)
                                   :meta meta
                                   :path path
                                   :val val
                                   :visited visited?)))
          (if (utils/namespaced-with? ref val)
            (resolve-path lh meta (utils/keyword-path val) (conj visited? path))
            path)))))

(defn replace-refs!
  [{{:keys [env ref]} :namespaces :keys [references] :as lh}
   metadata
   manifest
   & [{:keys [throw?] :or {throw? true} :as opts}]]
  (let [errors (atom [])
        result (walk/postwalk
                (fn [node]
                  (try
                    (cond
                      (utils/namespaced-with? ref node)
                      (let [keypath (utils/keyword-path node)
                            resolved-path (resolve-path lh metadata keypath #{})
                            resolved-value (if (vector? resolved-path)
                                             (get-in metadata resolved-path)
                                             resolved-path)]
                        (utils/logf lh "REF %s expanded to %s = %s" keypath resolved-path resolved-value)
                        ;; if we didn't resolve the metadata and were told to throw, die now
                        (when (and throw? (nil? resolved-path))
                          (->> keypath
                               (assoc (@references node) :path)
                               (throw-at-node! node "unresolved meta reference")))
                        (if resolved-path
                          (replace-refs! lh metadata resolved-value opts)
                          ;; if we didn't resolve the metadata but were told not to throw
                          ;; simply return the node as-is and let a subsequent replacement
                          ;; catch the issue.
                          node))

                      (utils/namespaced-with? env node)
                      (let [env-var (name node)]
                        (or (System/getenv env-var)
                            (let [data (merge {:env-var env-var} (@references node))]
                              (throw-at-node! node "unresolved env reference" data))))

                      :else
                      node)
                    (catch Exception e
                      (swap! errors conj e))))
                manifest)]
    (when-let [errors (not-empty @errors)]
      (utils/log lh (utils/prettify :edn metadata))
      (throw (ex-info (str/join "\n" (map #(.getMessage ^Exception %) errors)) {})))
    result))

;; Used only to bind into the eval-string call below (thus it's down here were more relevant).
(require 'lh.sci-tools)
(defn apply-fns!
  [lh {file :_file} spec]
  (walk/prewalk
   (fn [node]
     (if (list? node)
       (try
         (sci/eval-string (pr-str node) {:namespaces {'io {'file #(apply files/resolve lh %&)}
                                                      'json (ns-publics 'clojure.data.json)
                                                      'str (ns-publics 'clojure.string)
                                                      'tools (ns-publics 'lh.sci-tools)
                                                      'user {'slurp slurp}}})
         (catch Exception e
           (utils/log lh (utils/prettify :edn spec))
           (throw (ex-info
                   (format "could not evaluate list in '%s': '%s' due to: %s"
                           file
                           node
                           (str e))
                   {:node node}
                   e))))
       node))
   spec))

(defn resolve!
  [lh [metadata manifest] & [opts]]
  [metadata (apply-fns! lh metadata (replace-refs! lh metadata manifest opts))])

;;
;; Stage 4: Custom Processors
;;
;;   This stage is all about `:processor`s. If there's any context-specific processing
;;   a processor needs to do, now is the time when it gets done.
;;
;;   At this point, all the metadata has been applied, but it's passed along just in case it's
;;   needed.
;;

(defn apply-processor-dispatch
  [{:keys [processor]} [_metadata _manifest]]
  processor)
(defmulti apply-processor! #'apply-processor-dispatch)
(defmethod apply-processor! :default
  [_lh [_metadata manifest]]
  manifest)

;;
;; Stage 5: Canonicalization
;;
;;   This stage is all about finalizing the data into whatever form is desired. Everything has
;;   been processed at this point, all that's left is to apply the finishing touches.
;;

(defn camelCase
  "Recursively converts all keys in `data` to `camelCase`.

  Skips casing symbols to allow for specific output that may be against
  the output format's convention."
  [data]
  (letfn [(camelCase [value]
            (cond
              ;; Allow for symbols to skip the camelCasing
              (symbol? value) (str value)
              (not (keyword? value)) value
              :else (csk/->camelCaseString value)))
          (resolver [node]
            (cond
              (map? node) (cske/transform-keys camelCase node)
              :else node))]
    (walk/postwalk resolver data)))

(defn canonicalize!
  [{:keys [output-format]} manifest]
  (let [conventionalize (if (= :edn output-format) identity camelCase)]
    (->> manifest
         (conventionalize)
         (utils/sorted-maps)
         (utils/prettify output-format)
         (str/trim))))

;;
;; Main public API
;;

(def stages
  [::expand
   ::merge
   ::resolve
   ::process
   ::prettify])

(defn process!
  [lh through-stage lib & [opts]]
  (let [stages (conj (into [] (take-while #(not= through-stage %)) stages) through-stage)]
    (reduce (fn [data stage]
              (utils/logf lh "Running %s" stage)
              (case stage
                ::expand (expand! lh data)
                ::merge (merge-tree! lh data)
                ::resolve (resolve! lh data opts)
                ::process (apply-processor! lh data)
                ::prettify (canonicalize! lh data)))
            lib
            stages)))
