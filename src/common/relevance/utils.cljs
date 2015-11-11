(ns relevance.utils
  (:require [cljs.core.async :refer [<!]]
            [clojure.string :refer [lower-case]]
            [dommy.core :as dommy]
            [cognitect.transit :as transit])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))


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
        (lower-case))))

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
  (hash-string host))

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


(defn time-display
  "Returns a display string for a number of milliseconds"
  [seconds]
  (cond
    (< seconds 1) "< 1s"
    (< seconds 60) (str seconds "s")
    (< seconds 3600) (str (quot seconds 60) "min " (rem seconds 60) "s")
    (< seconds 86400) (str (quot seconds 3600) "h " (quot (rem seconds 3600) 60) "min")
    ;; TODO: 86592666 is returning "1d 0h", we should elide the lowest if it's 0
    :else (str (quot seconds 86400) "d " (quot (rem seconds 86400) 3600) "h")))