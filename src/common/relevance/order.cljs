(ns relevance.order
  (:require [relevance.utils :refer [on-channel url-key host-key hostname root is-http? ms-day]]
            [relevance.data :refer [accumulate-root-times]]))


;;;;------------------------------------
;;;; Settings
;;;;------------------------------------

(def non-http-penalty 0.01)
(def sound-extra-score 9888777666)


;;;;------------------------------------
;;;; Functions
;;;;------------------------------------

(defn time-score
  "Returns map containing a score for a tab based on the total time spent at
  both a URL and the site the URL belongs to, as well as a flag indicating
  if it's a priority tab."
  [tab url-times site-times settings]
  (let [url        (:url tab)
        idx        (:index tab)
        url-time   (or (:time (get url-times (url-key url)))
                       0)
        priority?  (and (:sound-to-left? settings)
                        (:audible tab))
        penalized? (and (not (is-http? url))
                        (not priority?))
        tab-time   (cond
                     ;; Add an extra score if it's a priority URL
                     priority? (+ sound-extra-score idx)
                     ;; If a URL is penalized, disregard the time
                     ;; and use its index (it'll get penalized later)
                     penalized? idx
                     ;; ... otherwise we just go with the raw URL time
                     :else url-time)
        host-time  (or (:time (get site-times (host-key (hostname url)))) 0)
        total      (+ tab-time host-time)
        score      (if penalized? (* total non-http-penalty) total)]
    ;; A tab without positive time in it will use its index as a small
    ;; offset to the host time. That way pages from the same host are
    ;; lumped together, and new tabs are sorted by index.
    {:score     (or (when (pos? tab-time) score)
                    (+ host-time (* 0.1 idx)))
     :priority? (not (or (nil? priority?)
                         (false? priority?)))}))


(defn score-and-sort-simple
  "Expects a group of tabs and associates a time score with each."
  [tabs url-times site-times settings]
  (->> tabs
       (map #(merge % (time-score % url-times site-times settings)))
       (sort-by #(- (:score %)))))


(defn sort-by-root
  "Returns a hashmap of the new tab ids and their indexes, based on a tab list and
  the score function for time spent on urls and sites."
  [tabs url-times site-times settings]
  (let [root-times (accumulate-root-times site-times)]
    ; (cljs.pprint/pprint tabs)
    ; (cljs.pprint/pprint root-times)
    (->> tabs
         ;; We first group them by root hostname, sort the tab subgroups,
         ;; and then sort the groups by the time spend on the root.
         (group-by #(root (hostname (:url %))))
         (map (fn [[k v]]
                (let [scored    (score-and-sort-simple v url-times site-times settings)
                      root-time (get root-times k)
                      ;; We may get a bunch of pages where the root time is 0.
                      ;; In that case, let's sort them by their accumulated score.
                      idx       (if (pos? root-time)
                                  root-time
                                  (apply + (map :score scored)))]
                  [(- idx)
                   scored])))
         (sort-by first)
         ;; Discard the root names and just flatten the list
         (map second)
         flatten
         ;; Once we have done this, we will need to take priority tabs into
         ;; account. Those will break from their main group and appear first.
         (map-indexed #(assoc %2 :index %1))
         (sort-by #(if (:priority? %)
                     (- (:score %))
                     (:index %)))
         ;; Ready!
         (map-indexed #(hash-map :index %1
                                 :id (:id %2))))))