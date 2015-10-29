(ns relevance.data
  (:require [relevance.utils :refer [to-transit from-transit]]
            [cljs.core.async :refer [<!]]
            [khroma.storage :as storage])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn save-raw
  "Saves the data raw, without converting it to transit first."
  [data callback]
  (storage/set {:data data} storage/local callback))

(defn save
  "Saves our data on the extension's storage after converting it to transit."
  [data]
  (save-raw (to-transit data) nil))


(defn load
  "Returns a channel where we'll put the entire data block read from the
  extension's storage"
  []
  (go
    (let [raw  (:data (<! (storage/get)))]
      (from-transit raw))))
