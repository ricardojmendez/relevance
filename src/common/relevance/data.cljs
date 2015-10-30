(ns relevance.data
  (:require [relevance.utils :refer [key-from-url]]))


(defn track-time
  "Receives a time database, a tab record and a time to track, and returns new
  time database which is the result of adding the time to the URL. It also
  timestamps the record with the timestamp received."
  [url-times tab time timestamp]
  (let [url      (or (:url tab) "")
        url-key  (key-from-url url)
        url-time (or (get url-times url-key)
                     {:url       url
                      :time      0
                      :timestamp 0})
        ;; Don't track two messages too close together
        track?   (not= 0 url-key)
        new-time (assoc url-time :time (+ (:time url-time) time)
                                 :title (:title tab)
                                 :favIconUrl (:favIconUrl tab)
                                 :timestamp timestamp)]
    (if track?
      (assoc url-times url-key new-time)
      url-time)))