(ns lh.graph
  (:require [clojure.string :as str]
            [dorothy.core :as dot]
            [hiccup.core :as html]
            [lh.files :as files]
            [lh.library :as lib]
            [lh.manifest :as manifest]
            [lh.utils :as utils])
  (:import [java.io File]))

(set! *warn-on-reflection* true)

(defn node-name
  "Returns the name for a graphvis node."
  [{:keys [root]} ^File f]
  (when f
    (let [regex (re-pattern (str root "/?"))]
      (-> f
          (.getCanonicalPath)
          (str/replace regex "")))))

(defn info-label
  "Generates the graphvis label for the `expanded` manifest tree."
  [lh files]
  (let [no-border {:border 0}
        left-align {:align "left"}
        file-key (utils/special-key lh :def :_file)
        pretty-rows (interpose
                     [:hr]
                     (map (fn [[basename libraries]]
                            (cons [:tr [:td basename]]
                                  (mapcat (fn [{:keys [group-path library]}]
                                            (map-indexed
                                             (fn [j node]
                                               [:tr
                                                [:td (if (zero? j) group-path "")]
                                                [:td left-align (node-name lh (node file-key))]])
                                             (lib/tree-nodes lh library)))
                                          libraries)))
                          files))]
    (html/html
     [:table
      (for [line ["Circles are actual files on disk"
                  "Blue squares are library file names being inherited"
                  "Purple nodes make up the manifest file groupings"]]
        [:tr [:td (merge no-border left-align) (str "- " line)]])
      [:tr [:td "Merge Order"]]
      [:tr
       [:td no-border
        [:table no-border pretty-rows]]]])))

(defn statements*
  "Recursively builds a list of statements starting from `this-node`."
  [lh this-node lib]
  (let [basename-key (utils/special-key lh :def :_basename)
        file-key (utils/special-key lh :def :_file)]
    (letfn [(node-id [suffix]
              (str this-node suffix))
            (tree-edges [lib]
              (let [basename (lib basename-key)
                    file (lib file-key)
                    parent-id (node-id basename)
                    parent-node [parent-id {:label basename
                                            :shape "box"
                                            :color :blue}]
                    filename (node-name lh file)
                    file-id (node-id filename)
                    file-node [file-id {:label filename}]
                    parent->file-edge [parent-id :> file-id]]
                (concat [parent-node file-node parent->file-edge]
                        (statements* lh file-id lib))))]
      (let [from (lib (utils/from-key lh))
            lib-edges (map (fn [basename]
                             [this-node :> (node-id basename)])
                           ;; parent libs have been expanded in-place by this point
                           ;; so we'd have multiple edges from this manifest to
                           ;; the parent lib's basename.
                           ;;
                           ;; to avoid the confusion with having multiple egdes pointing
                           ;; to the same node, we make these edges separately from
                           ;; the other tree edges.
                           (distinct (map basename-key from)))]
        (concat lib-edges (mapcat tree-edges from))))))

(defn statements
  [lh root-file files]
  (let [root (node-name lh root-file)]
    (mapcat (fn [[basename libraries]]
              (let [manifest-attrs {:color "purple"}
                    root-node [root manifest-attrs]
                    basename-node [basename manifest-attrs]]
                (->> libraries
                     (mapcat (fn [{:keys [group-path library]}]
                               (cond->> (statements* lh (or group-path basename) library)
                                 group-path (concat [[group-path (assoc manifest-attrs :shape "box")]
                                                      [basename :> group-path]]))))
                     (concat [root-node
                              basename-node
                              [root :> basename]]))))
            files)))

(defn visualize
  "Builds a graphvis dot representation of the inheritance tree for `manifest-file`."
  [lh manifest-file]
  (let [files (->> manifest-file
                   (files/read-edn lh)
                   (manifest/split! lh manifest-file)
                   (map (fn [[basename libraries]]
                          [basename (manifest/process-all! lh ::lib/expand basename libraries)])))
        edges (statements lh manifest-file files)
        graph-attributes {:label (info-label lh files)
                          :labelloc "t"
                          :labeljust "l"}]
    (->> edges
         (cons graph-attributes)
         (dot/digraph)
         (dot/dot))))
