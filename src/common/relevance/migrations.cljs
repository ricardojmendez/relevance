(ns relevance.migrations
  (:require
    [clojure.set :refer [rename-keys]]
    [relevance.data :refer [accumulate-site-times]]
    [relevance.utils :refer [url-key host-key hostname]]))


(defn migrate
  "Migrates a data set from its version to the next one. Returns the same
  data set if it cannot apply any migration."
  [data]
  (case (:data-version data)
    nil (->
          data
          (assoc :instance-id (or (:instance-id data)
                                  (.-uuid (random-uuid))))
          (assoc :data-version 1)
          (assoc :url-times (into {} (map #(vector (key %)
                                                   (dissoc (val %) :favIconUrl :icon))
                                          (:url-times data))))
          (assoc :site-times (accumulate-site-times (:url-times data))))
    1 (->
        data
        (assoc :data-version 2)
        (assoc :site-times (accumulate-site-times (:url-times data))))
    2 (let [url-times (into {}
                            (->>
                              (:url-times data)
                              (map #(vector (key %)
                                            (-> (val %)
                                                (assoc :time (quot (:time (val %)) 1000))
                                                (rename-keys {:timestamp :ts}))))
                              (remove #(zero? (:time (second %))))))]
        (assoc data
          :data-version 3
          :url-times url-times
          :site-times (accumulate-site-times url-times)))

    data))



(defn migrate-to-latest
  "Takes a data set and iterates on it until no more version migrations can be applied"
  [data]
  (loop [to-migrate data]
    (let [migrated (migrate to-migrate)]
      (if (not= migrated to-migrate)
        (recur migrated)
        migrated))))