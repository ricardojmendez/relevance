(ns relevance.test.data
  (:require [cljs.test :refer-macros [deftest testing is are]]
            [relevance.data :as data]
            [relevance.utils :as utils]
            ))



(def test-db
  {:instance-id  "67b5c8eb-ae97-42ad-b6bc-803ac7e31221"
   :suspend-info nil
   :url-times    {-327774960
                  {:url        "http://numergent.com/tags/khroma/",
                   :time       117300,
                   :timestamp  1445964037798,
                   :title      "Khroma articles",
                   :favIconUrl "http://numergent.com/favicon.ico"}
                  -526558523
                  {:url        "http://numergent.com/opensource/",
                   :time       27300,
                   :timestamp  1445964037798,
                   :title      "Open source projects",
                   :favIconUrl "http://numergent.com/favicon.ico"}
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


(deftest test-track-time
  (testing "Add time to an existing tab"
    (let [tab     {:url        "http://numergent.com/opensource/"
                   :title      "Open source project details"
                   :favIconUrl "http://numergent.com/favicon.png"}
          ts      1445964037799
          result  (data/track-time (:url-times test-db)
                                   tab
                                   1234
                                   ts)
          tab-key (utils/key-from-url "http://numergent.com/opensource/")
          item    (get result tab-key)]
      (is result)
      (is (= 5 (count result)) "We should get back the same number of elements")
      (is item)
      (are [k] (= (k item) (k tab)) :url :title :favIconUrl)          ; All the keys should have been updated from the record we're sending
      (is (= ts (:timestamp item)) "Item should have been time-stamped")
      (is (= 28534 (:time item)) "Time should have increased")
      (doseq [other (dissoc result tab-key)]
        (is (= (val other) (get (:url-times test-db) (key other))) "Other items should have remained untouched")
        )
      ))
  (testing "Add time to a new tab"
    (let [tab     {:url        "http://numergent.com/"
                   :title      "Numergent limited"
                   :favIconUrl "http://numergent.com/favicon.png"}
          ts      1445964037799
          result  (data/track-time (:url-times test-db)
                                   tab
                                   9001
                                   ts)
          tab-key (utils/key-from-url "http://numergent.com/")
          item    (get result tab-key)]
      (is result)
      (is (= 6 (count result)) "We should have an extra element")
      (is item)
      (are [k] (= (k item) (k tab)) :url :title :favIconUrl)          ; All the keys should have been updated from the record we're sending
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
          result  (data/track-time {} tab 9001 ts)
          tab-key (utils/key-from-url "http://numergent.com/")
          item    (get result tab-key)]
      (is result)
      (is (= 1 (count result)) "We should have an extra element")
      (is item)
      (are [k] (= (k item) (k tab)) :url :title :favIconUrl)          ; All the keys should have been updated from the record we're sending
      (is (= ts (:timestamp item)) "Item should have been time-stamped")
      (is (= 9001 (:time item)) "Time should have increased")
      ))
  )