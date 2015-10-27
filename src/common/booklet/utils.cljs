(ns booklet.utils
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
    (recur channel)
    ))


(defn to-transit
  [data]
  (transit/write (transit/writer :json) data))

(defn from-transit
  [transit-data]
  (transit/read (transit/reader :json) transit-data))
