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

(defn root
  "Returns the root domain for a host"
  [host]
  (->> (string/split (lower-case (or host ""))
                     #"\.")
       (take-last 2)
       (string/join ".")))

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

(defn host-key
  "Returns a key for a hostname, or 0 if the hostname is empty"
  [host]
  (if (not-empty host)
    (hash-string (trim (lower-case host)))
    0))

(defn url-key
  "Shortens a URL to remove anchor and protocol, and returns an integer based on
  the result."
  [url]
  (if (not-empty url)
    (let [element   (dommy/set-attr! (dommy/create-element :a) :href url)
          ;; For the url-key we _want_ to use the full host, not just the hostname
          ;; since it will be used to indicate how much time we have spent on the
          ;; specific URL. http://localhost/index.html will likely be different from
          ;; http://localhost:8080/index.html
          shortened (str (.toLowerCase (.-host element)) (.-pathname element) (.-search element))]
      (hash-string shortened))
    0))

(defn to-string-set
  "Split a string using commas, semi-colons or new lines, trims the resulting
  elements, and returns them as a set"
  [s]
  (->> (string/split (lower-case (or s ""))
                     #",|\n|;| ")
       (map string/trim)
       (remove empty?)
       (into #{})))

(defn time-display
  "Returns a display string for a number of seconds"
  [seconds]
  (letfn [(time-label [major major-label minor minor-label]
            (clojure.string/join
              (concat
                [major major-label]
                (when (pos? minor) [" " minor minor-label]))))]
    (cond
      (< seconds 1) "< 1s"
      (< seconds 60) (str seconds "s")
      (< seconds 3600) (time-label (quot seconds 60) "m" (rem seconds 60) "s")
      (< seconds 86400) (time-label (quot seconds 3600) "h" (quot (rem seconds 3600) 60) "m")
      :else (time-label (quot seconds 86400) "d" (quot (rem seconds 86400) 3600) "h"))))
