(ns booklet.utils
  (:require [re-frame.core :refer [dispatch dispatch-sync]]
            [cognitect.transit :as transit])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))


(defn dispatch-on-channel
  "Dispatches msg when there's content received on the channel returned by
  function chan-f."
  [msg chan-f]
  (go-loop
    [channel (chan-f)]
    (dispatch [msg (<! channel)])
    (recur channel)
    ))


(defn to-transit
  [data]
  (transit/write (transit/writer :json) data))

(defn from-transit
  [transit-data]
  (transit/read (transit/reader :json) transit-data)
  )
