(ns lh.processors.kube
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [lh.library :as lib]))

(set! *warn-on-reflection* true)

;;
;; Stage 3
;;

;; Resolves the image of a container.
;;
;;   If the image is a:
;;
;;    - string it is assumed to already be at its resolved form
;;    - keyword it'll be looked up in the metadata map.
(defmethod lib/resolve-path* :kube
  [{{{{:keys [namespace registry-key tags-key]} :tags} :kube} :processors} meta path _visited?]
  (when (= (name namespace) (name (first path)))
    (let [key (last path)
          registry (registry-key meta)
          tags (tags-key meta)]
      ;; If the image is a string, assume it's already the right image.
      (if (string? key)
        key
        (let [image-name (name key)]
          (format "%s/%s:%s" registry image-name (get tags key "latest")))))))

;;
;; Stage 4
;;

(defn- all-paths
  "Returns all key paths in the provided config"
  [config]
  (letfn [(children [path]
            (let [value (get-in config path)]
              (when (map? value)
                (map #(conj path %) (keys value)))))
          (branch? [path]
            (seq (children path)))]
    (->> config
         (keys)
         (map vector)
         (mapcat #(tree-seq branch? children %)))))

(defn- ->env-name
  "Translates the provided path into a cprop-compatible env var"
  [lh path]
  ;; Allow for "verbatim" environment keys by providing a sybmol
  ;;   `{:env {some.value "42"}}`
  ;;
  ;; Note the assumption that verbatim keys only exist at the root level.
  (let [{:keys [nest-str kebab-str]
         :or {nest-str "__"
              kebab-str "_"}} (-> lh :processors :kube)]
    (if (and (= 1 (count path)) (symbol? (first path)))
      (name (first path))
      (as-> path $
        (map name $)
        (str/join nest-str $)
        (str/replace $ "-" kebab-str)
        (str/upper-case $)))))

(defn- value-from
  "Attempts to resolve a potential referential config."
  [reference]
  (let [[type key-name] (str/split (namespace reference) #"\.")]
    ;; TODO: there are more of these valueFrom type of bindings. Find a good
    ;;       way to support them all.
    (if-let [resource-key (case type
                            "secret" :secret-key-ref
                            "config" :config-map-key-ref
                            "field" :field-ref
                            nil)]
      {:value-from {resource-key (if (= :field-ref resource-key)
                                   {:api-version "v1"
                                    :field-path (str key-name "." (name reference))}
                                   {:name key-name
                                    :key (name reference)})}}
      {:value reference})))

(defn- env-value
  "Returns the full kube `env` entry for the provided path."
  [lh path obj]
  (let [name (->env-name lh path)]
    (merge {:name name}
           (if (and (or (symbol? obj) (keyword? obj))
                    (not (nil? (namespace obj))))
             (value-from obj)
             ;; clj-yaml is smart enough to know:
             ;;   `(str 2)` -> `'2'`
             {:value (str obj)}))))

(defn- env-vars
  "Returns a vector of env vars from the provided config"
  [lh config]
  ;; (log/debug "Converting environment config")
  (->> config
       (all-paths)
       (remove #(map? (get-in config %)))
       (map #(env-value lh % (get-in config %)))
       (sort-by :name)
       (into [])))

(defn- map->named-vector
  "Converts the provided map of name/map pairs into a vector of its
  values sorted by :name.  Each key is added to its associated value
  under the `:name` key.

  Example:

  {:one {:hello \"world\"}} -> [{:name :one :hello \"world\"}]"
  [value]
  ;; (log/debug "Creating named vectors")
  (if (map? value)
    (->> value
         (map (fn [[key value]]
                (assoc value :name (keyword (name key)))))
         (sort-by :name)
         (vec))
    value))

(defmethod lib/apply-processor! :kube
  [lh [_metadata manifest]]
  ;; (log/infof "Applying :kube preset to %s" (:kind manifest))
  (walk/postwalk
   (fn [node]
     (cond
       (vector? node)
       (let [[key value] node]
         (condp contains? key
           #{:env} [key (env-vars lh value)]
           ;; Since order doesn't matter to these keys, they can be defined
           ;; as maps for easier merging.
           #{:ports :volumes :volume-mounts :containers :init-containers}
           [key (map->named-vector value)]

           node))
       :else node))
   manifest))
