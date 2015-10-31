(ns relevance.migrations
  (:require [relevance.utils :refer [url-key host-key hostname]]))


(defn accumulate-site-times [url-times]
  (->>
    (group-by #(hostname (:url %)) (vals url-times))
    (into {} (map #(vector (host-key (key %))
                           (hash-map :host (key %)
                                     :time (apply + (map :time (val %)))
                                     :favIconUrl (:favIconUrl (first (val %))))
                           )))
    ))


(defn migrate
  "Migrates a data set from its version to the next one. Returns the same
  data set if it cannot apply any migration."
  [data]
  (condp = (:data-version data)
    nil (->
          data
          (assoc :instance-id (or (:instance-id data)
                                  (.-uuid (random-uuid))))
          (assoc :data-version 1)
          (assoc :url-times (into {} (map #(vector (key %)
                                                   (dissoc (val %) :favIconUrl))
                                          (:url-times data))))
          (assoc :site-times (accumulate-site-times (:url-times data))))
    1 (->
        data
        (assoc :data-version 2)
        (assoc :site-times (accumulate-site-times (:url-times data))))
    data
    ))


(defn migrate-to-latest
  "Takes a data set and interates on it until no more version migrations can be applied"
  [data]
  (loop [to-migrate data]
    (let [migrated (migrate to-migrate)]
      (if (not= migrated to-migrate)
        (recur migrated)
        migrated))))