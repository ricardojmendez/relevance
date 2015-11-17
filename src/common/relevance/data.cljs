(ns relevance.data
  (:require [relevance.utils :refer [url-key host-key hostname]]))


(defn accumulate-site-times
  "Accumulates the total time for a site from a hashmap of URL times"
  [url-times]
  (->>
    (group-by #(hostname (:url %)) (vals url-times))
    (remove #(empty? (key %)))
    (map #(vector (host-key (key %))
                  (hash-map :host (key %)
                            :time (apply + (map :time (val %)))
                            :icon (:icon (first (val %))))
                  ))
    (into {})
    )
  )


(defn time-clean-up
  "Removes from url-times all the items that are older than cut-off-ts
  and which were viewed for less than min-seconds"
  [url-times cut-off-ts min-seconds]
  (into {} (remove #(and (< (:ts (val %))
                            cut-off-ts)
                         (< (:time (val %)) min-seconds))
                   url-times)))

(defn track-url-time
  "Receives a url time database, a tab record and a time to track, and returns
  new time database which is the result of adding the time to the URL. It also
  timestamps the record with the timestamp received."
  [url-times tab time timestamp & {:keys [ignore-set]}]
  (let [url      (or (:url tab) "")
        id       (url-key url)
        url-item (or (get url-times id)
                     {:url  url
                      :time 0
                      :ts   0})
        track?   (and (not= 0 id)
                      (not (contains? ignore-set (hostname url)))
                      (< 0 time))
        new-item (assoc url-item :time (+ (:time url-item) time)
                                 :title (:title tab)
                                 :ts timestamp)]
    (if track?
      (assoc url-times id new-item)
      url-times)))


(defn track-site-time
  "Receives a site time database, a tab record and a time to track, and returns
  new time database which is the result of adding the time to the site. It also
  timestamps the record with the timestamp received, and adds the favIconUrl of
  the tab as the one for the entire site."
  [site-times tab time timestamp & {:keys [ignore-set]}]
  (let [host      (hostname (or (:url tab) ""))
        id        (host-key host)
        site-item (or (get site-times id)
                      {:host host
                       :time 0
                       :ts   0})
        track?    (and (not= 0 id)
                       (not (contains? ignore-set host))
                       (< 0 time))
        new-item  (assoc site-item :time (+ (:time site-item) time)
                                   :icon (:favIconUrl tab)
                                   :ts timestamp)]
    (if track?
      (assoc site-times id new-item)
      site-times)))