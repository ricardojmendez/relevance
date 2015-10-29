(ns relevance.utils
  (:require [cljs.core.async :refer [<!]]
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


(defn key-from-url
  "Shortens a URL to remove anchor and protocol, and returns an integer based on
  the result."
  [url]
  (let [element   (.createElement js/document "a")
        _         (aset element "href" url)
        shortened (str (.-host element) (.-pathname element) (.-search element))]
    (hash-string shortened)))


(defn time-display
  "Returns a display string for a number of milliseconds"
  [millis]
  (let [seconds (quot millis 1000)]
    (cond
      (< seconds 1) "< 1s"
      (< seconds 60) (str seconds "s")
      (< seconds 3600) (str (quot seconds 60) "min " (rem seconds 60) "s")
      (< seconds 86400) (str (quot seconds 3600) "h " (quot (rem seconds 3600) 60) "min")
      :else (str (quot seconds 86400) "d " (quot (rem seconds 86400) 3600) "h"))
    ))