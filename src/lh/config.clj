(ns lh.config
  (:refer-clojure :exclude [load])
  (:require [clojure.java.io :as io]
            [lh.files :as files])
  (:import [java.io File]))

(set! *warn-on-reflection* true)

(def required-keys #{})
(def default-config
  {:output-format :edn
   :output-mode :file
   :verbose false
   :library-prefix "lib/"
   :metadata-prefix "meta/"
   :output-prefix "manifests/"
   ;; namespaces used by lighthouse to differentiate "special" keywords/symbols
   :namespaces
   {:def "def"
    :ref "ref"
    :env "env"
    :in "in"
    :group "group"}
   ;; processor specific configurations
   :processor nil
   :processors
   {:kube {:nest-str "__"
           :kebab-str "_"
           :tags {:namespace "tags"
                  :registry-key :registry
                  :tags-key :tags}}}})

(defn search-from
  "Starts searching for a `lighthouse.edn` config file up from `file`.

  Returns a tuple of `[config-file dirs-between-config-and-f]`."
  ([file]
   (search-from file []))
  ([^File file dirs]
   (when (files/exists? file)
     (let [current-dir (if (files/directory? file) file (.getParentFile file))
           lh-file (io/file current-dir "lighthouse.edn")]
       (if (files/exists? lh-file)
         [lh-file (reverse (conj dirs "."))]
         (recur (.getParentFile current-dir) (conj dirs (files/filename current-dir))))))))

(defn paths-from
  "Returns an iterative join of `dirs`."
  [dirs]
  (->> dirs
       (count)
       (inc)
       (range 1)
       (map #(vec (take % dirs)))))

(defn load
  "Loads and processes the provided `lighthouse.edn` app config. "
  [^File manifest-file]
  (let [^File absolute-manifest-file (some-> manifest-file files/absolute-file)
        [^File config-file dirs-between] (search-from absolute-manifest-file)]
    (when (or (nil? config-file) (not (files/exists? config-file)))
      (throw (ex-info "no config file found" {:config-file config-file})))
    (let [config (files/read-edn default-config config-file)
          root (.getParent config-file)
          ;; we're not dealing with a sub-directory if the parents of
          ;; `manifest-file` and `config-file` are the same.
          dir (if (and (.isFile absolute-manifest-file)
                       (= root (.getParent absolute-manifest-file)))
                ""
                (files/dirname absolute-manifest-file))
          config (-> default-config
                     (merge config)
                     (assoc :root root
                            :dir dir
                            :paths (paths-from dirs-between)
                            :references (atom {})))]
      ;; (println "using" (.getPath config-file) "for config")
      (when (->> required-keys
                 (map config)
                 (not-every? some?))
        (throw (ex-info "missing required key" config)))
      config)))
