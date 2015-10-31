(ns relevance.test.data
  (:require [cljs.test :refer-macros [deftest testing is are]]
            [relevance.data :as data]
            [relevance.migrations :as migrations]
            [relevance.utils :as utils]
            ))



(def test-db
  {:instance-id  "67b5c8eb-ae97-42ad-b6bc-803ac7e31221"
   :suspend-info nil
   :data-version 1
   :url-times    {-327774960
                  {:url       "http://numergent.com/tags/khroma/",
                   :time      117300,
                   :timestamp 1445964037798,
                   :title     "Khroma articles"}
                  -526558523
                  {:url       "http://numergent.com/opensource/",
                   :time      27300,
                   :timestamp 1445964037798,
                   :title     "Open source projects"}
                  -2272190
                  {:url       "http://lanyrd.com/conferences/",
                   :time      5617,
                   :timestamp 1446047687895,
                   :title     "Conferences and events worldwide | Lanyrd"}
                  -327358142
                  {:url       "https://developer.chrome.com/extensions/contextMenus",
                   :time      901682,
                   :timestamp 1446028215734,
                   :title     "chrome.contextMenus - Google Chrome"}
                  1917381154
                  {:url       "http://www.kitco.com/market/",
                   :time      4432,
                   :timestamp 1446051494575,
                   :title     "New York spot price Gold..."
                   }
                  }
   :site-times   {971841386   {:favIconUrl "http://numergent.com/favicon.ico"
                               :time       144600
                               :host       "numergent.com"}
                  -1466097211 {:favIconUrl nil
                               :time       5617
                               :host       "lanyrd.com"}
                  -331299663  {:favIconUrl "https://www.google.com/images/icons/product/chrome-32.png"
                               :time       901682
                               :host       "developer.chrome.com"}
                  -915908674  {:favIconUrl nil
                               :time       4432
                               :host       "www.kitco.com"}}})


(deftest test-host-key
  ;;Test the host-key function against known values
  (are [host key] (= (utils/host-key host) key)
                  "numergent.com" 971841386
                  "google.com" -1536293812
                  "startpage.com" 920793092
                  "" 0
                  nil 0))


(deftest test-track-url-time
  (testing "Add time to an existing tab"
    (let [tab    {:url        "http://numergent.com/opensource/"
                  :title      "Open source project details"
                  :favIconUrl "http://numergent.com/favicon.png"}
          ts     1445964037799
          result (data/track-url-time (:url-times test-db)
                                      tab
                                      1234
                                      ts)
          id     (utils/url-key "http://numergent.com/opensource/")
          item   (get result id)]
      (is result)
      (is (= 5 (count result)) "We should get back the same number of elements")
      (is item)
      (is (nil? (:favIconUrl item)))
      (are [k] (= (k item) (k tab)) :url :title)                      ; All the keys should have been updated from the record we're sending
      (is (= ts (:timestamp item)) "Item should have been time-stamped")
      (is (= 28534 (:time item)) "Time should have increased")
      (doseq [other (dissoc result id)]
        (is (= (val other) (get (:url-times test-db) (key other))) "Other items should have remained untouched")
        )
      ))
  (testing "Add time to a new tab"
    (let [tab     {:url        "http://numergent.com/"
                   :title      "Numergent limited"
                   :favIconUrl "http://numergent.com/favicon.png"}
          ts      1445964037799
          result  (data/track-url-time (:url-times test-db)
                                       tab
                                       9001
                                       ts)
          tab-key (utils/url-key "http://numergent.com/")
          item    (get result tab-key)]
      (is result)
      (is (= 6 (count result)) "We should have an extra element")
      (is item)
      (are [k] (= (k item) (k tab)) :url :title)                      ; All the keys should have been updated from the record we're sending
      (is (= ts (:timestamp item)) "Item should have been time-stamped")
      (is (= 9001 (:time item)) "Time should have been assigned")
      (doseq [other (dissoc result tab-key)]
        (is (= (val other) (get (:url-times test-db) (key other))) "Other items should have remained untouched")
        )
      ))
  (testing "Add time to a new tab on an empty set"
    (let [tab     {:url        "http://numergent.com/"
                   :title      "Numergent limited"
                   :favIconUrl "http://numergent.com/favicon.png"}
          ts      12345
          result  (data/track-url-time {} tab 9001 ts)
          tab-key (utils/url-key "http://numergent.com/")
          item    (get result tab-key)]
      (is result)
      (is (= 1 (count result)) "We should have an extra element")
      (is item)
      (are [k] (= (k item) (k tab)) :url :title)                      ; All the keys should have been updated from the record we're sending
      (is (= ts (:timestamp item)) "Item should have been time-stamped")
      (is (= 9001 (:time item)) "Time should have increased")
      ))
  )


(deftest test-track-site-time
  (testing "Add time to an empty database"
    (let [tab    {:url        "http://numergent.com/opensource/"
                  :title      "Open source project details"
                  :favIconUrl "http://numergent.com/favicon.png"}
          ts     1445964037799
          result (data/track-site-time {}
                                       tab
                                       1234
                                       ts)
          id     (utils/host-key (utils/hostname "http://numergent.com/opensource/"))
          item   (get result id)]
      (is result)
      (is (= 1 (count result)) "We should get back a single element")
      (is item)
      (are [expected result] (= expected result)
                             (:favIconUrl tab) (:favIconUrl item)
                             "numergent.com" (:host item)
                             ts (:timestamp item)
                             1234 (:time item)))
    )
  (testing "Add time to an existing database for an existing site"
    (let [tab    {:url        "http://numergent.com/opensource/index.html"
                  :title      "Further open source project details"
                  :favIconUrl "http://numergent.com/newfavicon.png"}
          ts     1445964037900
          result (data/track-site-time (:site-times test-db)
                                       tab
                                       1234
                                       ts)
          id     (utils/host-key (utils/hostname "http://numergent.com/opensource/"))
          item   (get result id)]
      (is result)
      (is (= 4 (count result)) "We should get back four sites")
      (is item)
      (are [expected result] (= expected result)
                             (:favIconUrl tab) (:favIconUrl item)
                             "numergent.com" (:host item)
                             ts (:timestamp item)
                             145834 (:time item))
      (doseq [other (dissoc result id)]
        (is (= (val other) (get (:site-times test-db) (key other))) "Other items should have remained untouched")
        )))
  (testing "Add time to an existing database for a new site"
    (let [tab    {:url        "https://twitter.com/ArgesRic"
                  :title      "ArgesRic"
                  :favIconUrl "https://abs.twimg.com/favicons/favicon.ico"}
          ts     1445964037920
          result (data/track-site-time (:site-times test-db)
                                       tab
                                       9001
                                       ts)
          id     (utils/host-key (utils/hostname "https://twitter.com/EvenSomeOtherUrl"))
          item   (get result id)]
      (is result)
      (is (= 5 (count result)) "We should get back five sites")
      (is item)
      (are [expected result] (= expected result)
                             (:favIconUrl tab) (:favIconUrl item)
                             "twitter.com" (:host item)
                             ts (:timestamp item)
                             9001 (:time item))
      (doseq [other (dissoc result id)]
        (is (= (val other) (get (:site-times test-db) (key other))) "Other items should have remained untouched")
        ))
    )
  )


