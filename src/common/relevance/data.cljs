(ns relevance.data
  (:require [relevance.utils :refer [url-key host-key hostname]]))


(defn track-url-time
  "Receives a url time database, a tab record and a time to track, and returns
  new time database which is the result of adding the time to the URL. It also
  timestamps the record with the timestamp received."
  [url-times tab time timestamp]
  (let [url      (or (:url tab) "")
        id       (url-key url)
        url-item (or (get url-times id)
                     {:url       url
                      :time      0
                      :timestamp 0})
        track?   (not= 0 id)
        new-item (assoc url-item :time (+ (:time url-item) time)
                                 :title (:title tab)
                                 :timestamp timestamp)]
    (if track?
      (assoc url-times id new-item)
      url-times)))


(defn track-site-time
  "Receives a site time database, a tab record and a time to track, and returns
  new time database which is the result of adding the time to the site. It also
  timestamps the record with the timestamp received, and adds the favIconUrl of
  the tab as the one for the entire site."
  [site-times tab time timestamp]
  (let [host      (hostname (or (:url tab) ""))
        id        (host-key host)
        site-item (or (get site-times id)
                      {:host      host
                       :time      0
                       :timestamp 0})
        track?    (not= 0 id)
        new-item  (assoc site-item :time (+ (:time site-item) time)
                                   :favIconUrl (:favIconUrl tab)
                                   :timestamp timestamp)]
    (if track?
      (assoc site-times id new-item)
      site-times)))