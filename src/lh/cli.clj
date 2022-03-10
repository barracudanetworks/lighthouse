(ns lh.cli
  (:require [cli-matic.core :as cli]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [borkdude.rewrite-edn :as rewrite]
            [dorothy.jvm :as svg]
            [lh.config :as config]
            [lh.files :as files]
            [lh.graph :as graph]
            [lh.manifest :as manifest]
            [lh.utils :as utils])
  (:gen-class))

;; get rid of the grapical elements in dororothy as they
;; break Linux native image builds
(alter-var-root #'svg/get-frame (constantly nil))
(alter-var-root #'svg/show! (constantly nil))

(set! *warn-on-reflection* true)

(def response-code
  "Values return from `cli-matic` handlers are treated as response codes.

  The fn is to make that explicit."
  identity)

(defmacro defclifn
  [name bindings & body]
  `(defn ~name
     ~bindings
     (try
       ~@body
       (response-code 0)
       (catch Exception e#
         (utils/log* (pr-str e#))
         (response-code 1)))))

(defn- file-list
  "Expands `file` into a list of files.

  If `file` is a directory, all child EDN files will be returned."
  [file]
  (if (files/directory? file)
    (files/file-list file)
    [file]))

(defn parse-value
  "Parses an env value using `edn/read-string`.

  On reading a symbol or failing to parse the string, the original value is returned."
  [value]
  (try
    (let [parsed (edn/read-string value)]
      (if (symbol? parsed)
        value
        parsed))
    (catch Exception _
      value)))

(defn- split-pairs
  "Splits `pairs-string` into a vector of `[path value]` tuples."
  [pairs-string]
  (into [] (comp
            (map str/trim)
            (map (fn [part]
                   (let [[path-str value] (map str/trim (str/split part #"="))]
                     (when-not (and path-str value)
                       (throw (ex-info (format "invalid --set flag: '%s'" part) {:flag set})))
                     [(utils/keyword-path path-str) (parse-value value)]))))
        (str/split pairs-string #",")))

;;
;; CLI handlers
;;

(defclifn graph
  [{:keys [output] files :_arguments}]
  (doseq [manifest-file (filter files/exists? (map io/file files))
          :let [lh (config/load manifest-file)]]
    (let [graph (graph/visualize lh manifest-file)
          save-file (io/file output)]
      (svg/save! graph save-file {:format (files/extension save-file)}))))

(defclifn update!
  [{pairs :set files :_arguments}]
  (let [values-map (into {} (mapcat split-pairs) pairs)]
    (doseq [file (map io/file files)
            :let [data (rewrite/parse-string (slurp file))
                  updated (reduce (fn [acc [path value]]
                                    (rewrite/assoc-in acc path value))
                                  data
                                  values-map)]]
      (spit file (str updated)))))

(defclifn build!
  [{:keys [override-config] paths :_arguments}]
  (let [lh-and-files (into [] (comp
                               (map io/file)
                               (filter files/exists?)
                               (map (juxt config/load file-list)))
                           paths)
        errors (atom nil)]
    (try
      (doseq [[lh manifest-files] lh-and-files
              :let [lh (utils/deep-merge lh override-config)]
              manifest-file manifest-files
              [basename libraries] (manifest/split! lh manifest-file (files/read-edn lh manifest-file))]
        (utils/logf lh "%s has %s lib(s)" basename (count libraries))
        (->> libraries
             (manifest/process-all! lh basename)
             (manifest/write! lh basename)))
      (catch Exception e
        (utils/log* (pr-str e))
        (swap! errors conj (.getMessage e))))
    (when-let [errors @errors]
      (throw (Exception. (str/join "\n" errors))))))

(def config
  {:command "lh"
   :description "A tool for reading and combining EDN hierarchies"
   :version (str/trim (slurp (io/resource "VERSION")))
   :subcommands [{:command "build"
                  :description "Generates output manifests for all provided files."
                  :runs build!
                  :opts [{:as ["an EDN map that will override that found in lighthouse.edn"]
                          :option "override-config"
                          :short "o"
                          :type :edn
                          :default {}}]}
                 {:command "update"
                  :description "Updates 1-to-many values in 1-to-many given files"
                  :runs update!
                  :opts [{:as ["a CSV of path/value pairs to set. Can be given multiple times."]
                          :option "set"
                          :short "s"
                          :multiple true
                          :type :string
                          :default :present}]}
                 {:command "visualize"
                  :description "Renders a graphvis visualization of the provided file"
                  :runs graph
                  :opts [{:as ["output graphvis rendering to this file"]
                          :option "output"
                          :short "o"
                          :type :string
                          :default :present}]}]})

(defn -main [& args]
  (cli/run-cmd args config))
