(ns relevance.io
  (:require [relevance.utils :refer [to-transit from-transit url-key]]
            [cljs.core.async :refer [<!]]
            [khroma.storage :as storage])
  (:require-macros [cljs.core.async.macros :refer [go]]))


;;;; We keep all the Chrome-specific i/o functions in a single file so that
;;;; can just have a separate namespace for data transformations

(defn save-raw
  "Saves the data raw, without converting it to transit first."
  [id data callback]
  (storage/set {id data} storage/local callback))

(defn save
  "Saves our data on the extension's storage after converting it to transit."
  ([id data]
   (save id data nil))
  ([id data callback]
   (save-raw id (to-transit data) callback)))


(defn load
  "Returns a channel where we'll put the entire data block read from the
  extension's storage"
  [id]
  (go
    (let [raw (id (<! (storage/get)))]
      (from-transit raw))))
