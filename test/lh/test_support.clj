(ns lh.test-support
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [lh.config :as config]
            [lh.files :as files]))

(def lh (config/load (io/file (io/resource "test/no-parents.edn"))))

(defn node-path*
  "Returns the path from `root` to `node` as a sequence of directory keywords."
  [node]
  (let [node-path (.getCanonicalPath node)]
    (-> node-path
        (str/replace (re-pattern (:root lh)) "")
        (str/split #"/")
        (->> (map #(or (not-empty (files/basename (io/file %))) "."))
             (map keyword)))))

(def test-tree
  "Builds a representation of our test lighthouse app as an in-memory structure for
  easy refrencing."
  (->> lh
       (:root)
       (io/file)
       (file-seq)
       (rest)
       (into {} (comp
                 (remove files/directory?)
                 (map (juxt node-path* (partial files/read-edn lh)))))))

(defn node
  "Returns a node in the tree."
  [& path]
  (get test-tree path))

(defn tree
  "Forces `node` to inherit from `parents`."
  [node parents]
  (assoc node :def/from parents))

(defmacro with-tmp-files [bindings & body]
  (let [symbols (vec (take-nth 2 bindings))]
    `(let ~bindings
       (doseq [f# ~symbols
               :let [extension# (second (str/split (.getName f#) #"\."))]]
         (if extension#
           (.createNewFile f#)
           (.mkdir f#)))
       (try
         ~@body
         (finally
           ;; Delete in reverse so we delete files first
           (doseq [f# (reverse ~symbols)]
             (.delete f#)))))))
