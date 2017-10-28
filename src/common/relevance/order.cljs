(ns relevance.order
  (:require [relevance.utils :refer [on-channel url-key host-key hostname is-http? ms-day]]))


;;;;------------------------------------
;;;; Settings
;;;;------------------------------------

(def non-http-penalty 0.01)
(def sound-extra-score 9888777666)


;;;;------------------------------------
;;;; Functions
;;;;------------------------------------

(defn time-score
  "Returns a score for a tab based on the total time spent at both a URL and
  the site the URL belongs to."
  [tab url-times site-times settings]
  (let [url           (:url tab)
        idx           (:index tab)
        url-time      (or (:time (get url-times (url-key url)))
                          0)
        is-priority?  (and (:sound-to-left? settings)
                           (:audible tab))
        is-penalized? (and (not (is-http? url))
                           (not is-priority?))
        tab-time      (cond
                        ;; Add an extra score if it's a priority URL
                        is-priority? (+ sound-extra-score idx)
                        ;; If a URL is penalized, we want it to at least have a
                        ;; value of 1, otherwise the tab time gets ignored and
                        ;; we'd default to using the raw site time
                        is-penalized? (max url-time 1)
                        ;; ... otherwise we just go with the raw URL time
                        :else url-time)
        host-time     (or (:time (get site-times (host-key (hostname url)))) 0)
        total         (+ tab-time host-time)
        score         (if is-penalized? (* total non-http-penalty) total)]
    (or (when (pos? tab-time) score)
        (- host-time idx))))


(defn score-tabs
  "Returns a hashmap of the new tab ids and their indexes, based on a tab list and
  the score function for time spent on urls and sites."
  [tabs url-times site-times settings]
  (->> tabs
       (map #(assoc % :time (time-score % url-times site-times settings)))
       (sort-by #(* -1 (:time %)))
       (map-indexed #(hash-map :index %1
                               :id (:id %2)))))





