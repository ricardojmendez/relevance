(ns relevance.utils
  (:require [cljs.core.async :refer [<!]]
            [clojure.string :refer [lower-case trim]]
            [dommy.core :as dommy]
            [cognitect.transit :as transit]
            [clojure.string :as string])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))



;;;;-------------------------------------
;;;; Values
;;;;-------------------------------------


(def ms-hour (* 60 60 1000))
(def ms-day 86400000)
(def ms-week (* 7 ms-day))

;;;;-------------------------------------
;;;; Functions
;;;;-------------------------------------


(defn on-channel
  "Dispatches msg when there's content received on the channel returned by
  function chan-f. Expects a dispatch function."
  [chan-fn dispatch-fn msg]
  (go-loop
    [channel (chan-fn)]
    (dispatch-fn [msg (<! channel)])
    (recur channel)))


(defn to-transit
  [data]
  (transit/write (transit/writer :json) data))


(defn from-transit
  [transit-data]
  (transit/read (transit/reader :json) transit-data))


(defn hostname
  "Returns the host name for a URL, disregarding port and protocol"
  [url]
  (when url
    (-> (dommy/create-element :a)
        (dommy/set-attr! :href url)
        (.-hostname)
        lower-case
        trim)))

(defn protocol
  "Returns the protocol for a URL"
  [url]
  (when url
    (-> (dommy/create-element :a)
        (dommy/set-attr! :href url)
        (.-protocol)
        (lower-case))))

(defn is-http?
  "Returns true if the string starts with http: or https:"
  [url]
  (and (some? url)
       (some? (re-find #"\bhttps?:" (protocol url)))))

(defn host-key [host]
  (if (not-empty host)
    (hash-string (trim (lower-case host)))
    0))

(defn url-key
  "Shortens a URL to remove anchor and protocol, and returns an integer based on
  the result."
  [url]
  (if (not-empty url)
    (let [element   (-> (dommy/create-element :a)
                        (dommy/set-attr! :href url))
          shortened (str (.toLowerCase (.-host element)) (.-pathname element) (.-search element))]
      (hash-string shortened))
    0))

(defn to-string-set
  "Split a string into a string set using commas, semi-colons or new lines, and returns it as a set"
  [s]
  (->>
    (string/split (or s "") #",|\n|;| ")
    (map string/trim)
    (remove empty?)
    (map string/lower-case)
    (into #{})))

(defn time-display
  "Returns a display string for a number of seconds"
  [seconds]
  (letfn [(time-label [major major-label minor minor-label]
            (apply str (concat [major major-label]
                               (when (< 0 minor) [" " minor minor-label]))))]
    (cond
      (< seconds 1) "< 1s"
      (< seconds 60) (str seconds "s")
      (< seconds 3600) (time-label (quot seconds 60) "m" (rem seconds 60) "s")
      (< seconds 86400) (time-label (quot seconds 3600) "h" (quot (rem seconds 3600) 60) "m")
      :else (time-label (quot seconds 86400) "d" (quot (rem seconds 86400) 3600) "h")))
  )