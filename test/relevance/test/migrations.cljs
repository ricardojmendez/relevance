(ns relevance.test.migrations
  (:require [cljs.test :refer-macros [deftest testing is are]]
            [relevance.migrations :as migrations]
            [relevance.utils :as utils]
            ))

(def base-data
  {:instance-id  "67b5c8eb-ae97-42ad-b6bc-803ac7e31221"
   :suspend-info nil
   :url-times    {1274579744
                  {:url        "https://developer.chrome.com/extensions/examples/api/contextMenus/basic/sample.js",
                   :time       117300,
                   :timestamp  1445964037798,
                   :title      "https://developer.chrome.com/extensions/examples/api/contextMenus/basic/sample.js",
                   :favIconUrl "https://developer.chrome.com/favicon.ico"}
                  -1400165536
                  {:url        "https://www.polygon.com/2015/10/27/9623950/gravity-rush-2-ps4-north-america",
                   :time       14711,
                   :timestamp  1446036279627,
                   :title      "Gravity Rush 2 confirmed for North America | Polygon",
                   :favIconUrl "https://cdn2.vox-cdn.com/community_logos/42931/favicon.ico"}
                  -2272190
                  {:url        "http://lanyrd.com/conferences/",
                   :time       5617,
                   :timestamp  1446047687895,
                   :title      "Conferences and events worldwide | Lanyrd",
                   :favIconUrl nil}
                  -327358142
                  {:url        "https://developer.chrome.com/extensions/contextMenus",
                   :time       901682,
                   :timestamp  1446028215734,
                   :title      "chrome.contextMenus - Google Chrome",
                   :favIconUrl "https://www.google.com/images/icons/product/chrome-32.png"}
                  1917381154
                  {:url        "http://www.kitco.com/market/",
                   :time       4432,
                   :timestamp  1446051494575,
                   :title      "New York spot price Gold...",
                   :favIconUrl nil
                   }
                  }})


(deftest validate-key-fn
  (testing "Make sure our test ids are still valid"
    (doseq [[k v] (:url-times base-data)]
      (is (= k (utils/url-key (:url v))) (str "URL " (:url v))))))


(deftest test-migrations
  (testing "Empty migration"
    (let [new-data   (migrations/migrate {})
          without-id (dissoc new-data :instance-id)]
      (is (string? (:instance-id new-data)))
      (is (= without-id {:data-version 1, :url-times {}, :site-times {}}))
      ))
  (testing "v1 migration"
    (let [v1 (migrations/migrate base-data)]
      (is (not= v1 base-data))
      (is (= 5 (count v1)) "We should have received five keys")
      (are [k] (some? (k v1)) :url-times :instance-id :data-version :site-times)
      (is (= (:instance-id v1) (:instance-id base-data)) "Instance id should be preserved")
      (is (= 1 (:data-version v1)) "Data should have been tagged with the version")
      (doseq [[k v] (:url-times v1)]
        (is (integer? k))
        (is (nil? (:favIconUrl v)))
        )
      (is (= (:site-times v1) {-331299663  {:host "developer.chrome.com" :time 1018982 :favIconUrl "https://developer.chrome.com/favicon.ico"}
                               -967938826  {:host "www.polygon.com" :time 14711 :favIconUrl "https://cdn2.vox-cdn.com/community_logos/42931/favicon.ico"}
                               -1466097211 {:host "lanyrd.com" :time 5617 :favIconUrl nil}
                               -915908674  {:host "www.kitco.com" :time 4432 :favIconUrl nil}})
          "Site data should have been aggregated")
      ;; Test recurrent migration
      (is (= v1 (migrations/migrate-to-latest base-data)) "Migrating all the way to the latest should yield the same v1 data")
      (is (= v1 (migrations/migrate-to-latest v1)) "Migrating all the way to the latest should yield the same v1 data")
      (is (not= base-data (migrations/migrate-to-latest base-data)) "Migration loop should have returned a different data set")
      ))
  )

