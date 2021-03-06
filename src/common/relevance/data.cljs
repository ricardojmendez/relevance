(ns relevance.data
  "Contains functions related to data tracking and accumulation.
  It does not account for the actual ordering based on this data. You should
  see `relevance.order` for that."
  (:require [relevance.utils :refer [url-key is-http? host-key hostname root]]
            [khroma.log :as console]))


(defn accumulate-site-times
  "Accumulates the total time for a site from a hashmap of URL times.

  Returns a hashmap with the URL ID as the key, and the :time, :icon and
  :host string on its value.

  This function differentiates between hostnames on the different root domain.
  This means that docs.gitlab.com and gitlab.com are accumulated separately.
  
  While we could lump them together into one at this point, keeping them
  separately will allows us to apply the same weight to pages on the same
  hostname, which will lead to more natural ordering."
  [url-times]
  (->>
    (group-by #(hostname (:url %)) (vals url-times))
    (remove #(empty? (key %)))
    (map #(vector (host-key (key %))
                  (hash-map :host (key %)
                            :time (apply + (map :time (val %)))
                            :icon (:icon (first (val %))))))
    (into {})))


(defn accumulate-root-times
  "Expects the hashmap resulting from `accumulate-site-times`, and returns a
  new hashmap where times are accumulated by the root name.

  This will let us prioritize the pages in the same root domain together,
  while still keeping the per-site ordering."
  [site-times]
  (->> (group-by #(root (:host %)) (vals site-times))
       (remove #(empty? (key %)))
       (map #(vector (key %)
                     (apply + (map :time (val %)))))
       (into {})))



(defn clean-up-by-time
  "Removes from url-times all the items that are older than cut-off-ts
  and which were viewed for less than min-seconds"
  [url-times cut-off-ts min-seconds]
  (into {} (remove #(and (< (:ts (val %))
                            cut-off-ts)
                         (< (:time (val %)) min-seconds))
                   url-times)))

(defn clean-up-ignored
  "Removes from url-times all the items for which the domain
  matches an ignore set"
  [url-times ignore-set]
  (into {} (remove #(contains? ignore-set (hostname (:url (val %))))
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
                      (is-http? url)
                      (not (contains? ignore-set (hostname url)))
                      (pos? time))
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
  (let [url       (:url tab)
        host      (hostname (or url ""))
        id        (host-key host)
        site-item (or (get site-times id)
                      {:host host
                       :time 0
                       :ts   0})
        track?    (and (not= 0 id)
                       (is-http? url)
                       (not (contains? ignore-set host))
                       (pos? time))
        new-item  (assoc site-item :time (+ (:time site-item) time)
                                   :icon (:favIconUrl tab)
                                   :ts timestamp)]
    (if track?
      (assoc site-times id new-item)
      site-times)))