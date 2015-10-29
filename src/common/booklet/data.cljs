(ns booklet.data
  (:require [booklet.utils :refer [to-transit from-transit]]
            [cljs.core.async :refer [<!]]
            [khroma.storage :as storage])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn set
  "Saves our data block on the extension's storage"
  [data]
  (storage/set {:data (to-transit data)}))

(defn get
  "Returns a channel where we'll put the entire data block read from the
  extension's storage"
  []
  (go
    (let [raw  (:data (<! (storage/get)))]
      (from-transit raw))))
