(ns lh.sci-tools
  "A namespace for mapped into the `tools` namepsace for fn calls."
  (:require [camel-snake-kebab.core :as csk])
  (:import [java.time ZonedDateTime ZoneOffset]
           [java.time.format DateTimeFormatter]
           [java.util Base64]))

(set! *warn-on-reflection* true)

(defn iso-timestamp-now
  "Returns an ISON timestamp."
  []
  (-> (ZonedDateTime/now ZoneOffset/UTC)
      (.format DateTimeFormatter/ISO_INSTANT)))

(defn b64-encode
  "Base64 Encodes `text`."
  [^String text]
  (.encodeToString (Base64/getEncoder) (.getBytes text)))

(def camel-case csk/->camelCaseString)
(def kebab-case csk/->kebab-case-string)
(def snake-case csk/->snake_case_string)
