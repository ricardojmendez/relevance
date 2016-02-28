(ns relevance.test.data
  (:require [cljs.test :refer-macros [deftest testing is are]]
            [relevance.data :as data]
            [relevance.utils :as utils]
            ))

;; IMPORTANT: READ THIS BEFORE YOU START MODIFYING THESE TESTS
;;
;; You'll notice that when we pass the tab information as a parameter for one
;; of the calls, we'll use :favIconUrl instead of :icon, which is the one that
;; we actually keep.
;;
;; This is because :favIconUrl is the value we actually get from Chrome. Our
;; own functions convert it to :icon when saving it. That's why I still need to
;; use it for the parameters that simulate tabs.


(def test-db
  {:instance-id  "67b5c8eb-ae97-42ad-b6bc-803ac7e31221"
   :suspend-info nil
   :data-version 1
   :url-times    {-327774960
                  {:url   "http://numergent.com/tags/khroma/"
                   :time  117
                   :ts    1445964037798
                   :title "Khroma articles"}
                  -526558523
                  {:url   "http://numergent.com/opensource/"
                   :time  27
                   :ts    1445964037798
                   :title "Open source projects"}
                  -2272190
                  {:url   "http://lanyrd.com/conferences/"
                   :time  5
                   :ts    1446047687895
                   :title "Conferences and events worldwide | Lanyrd"}
                  -327358142
                  {:url   "https://developer.chrome.com/extensions/contextMenus"
                   :time  901
                   :ts    1446028215734
                   :title "chrome.contextMenus - Google Chrome"}
                  1917381154
                  {:url   "http://www.kitco.com/market/"
                   :time  4
                   :ts    1446051494575
                   :title "New York spot price Gold..."
                   }
                  ;; The next two items are used for pruning tests. Notice that
                  ;; prismatic has less time than splunk but was accessed later.
                  ;; Changing those values would break the tests.
                  1609181525
                  {:url   "http://getprismatic.com/oauth-complete?result=login"
                   :time  3
                   :ts    1446114615912
                   :title "Prismatic login"

                   }
                  1038158073
                  {:url   "http://splunk.com/"
                   :time  29
                   :ts    1446028215912
                   :title "Splunk"
                   }}
   :site-times   {971841386   {:icon "http://numergent.com/favicon.ico"
                               :time 144
                               :host "numergent.com"}
                  -1466097211 {:icon nil
                               :time 5
                               :host "lanyrd.com"}
                  -331299663  {:icon "https://www.google.com/images/icons/product/chrome-32.png"
                               :time 901
                               :host "developer.chrome.com"}
                  -915908674  {:time 4
                               :host "www.kitco.com"}
                  1366860619  {:time 3
                               :host "getprismatic.com"}
                  1557509622  {:time 29
                               :host "splunk.com"}}})


(deftest test-host-key
  ;;Test the host-key function against known values
  (are [host key] (= (utils/host-key host) key)
                  "numergent.com" 971841386
                  "google.com" -1536293812
                  "startpage.com" 920793092
                  "" 0
                  nil 0))

(deftest test-url-key
  ;;Test the url-key function against known values
  (are [url key] (= (utils/url-key url) key)
                 "http://numergent.com/articles/" -925262547
                 "https://google.com/search?q=v" 633277110
                 "https://startpage.com/my/settings#hash" 317544347
                 "http://getprismatic.com/oauth-complete?result=login" 1609181525
                 "http://splunk.com/" 1038158073
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
                                      2
                                      ts)
          id     (utils/url-key "http://numergent.com/opensource/")
          item   (get result id)]
      (is result)
      (is (= 7 (count result)) "We should get back the same number of elements")
      (is item)
      (is (nil? (:icon item)))
      (are [k] (= (k item) (k tab)) :url :title)                      ; All the keys should have been updated from the record we're sending
      (is (= ts (:ts item)) "Item should have been time-stamped")
      (is (= 29 (:time item)) "Time should have increased")
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
                                       9
                                       ts)
          tab-key (utils/url-key "http://numergent.com/")
          item    (get result tab-key)]
      (is result)
      (is (= 8 (count result)) "We should have an extra element")
      (is item)
      (are [k] (= (k item) (k tab)) :url :title)                      ; All the keys should have been updated from the record we're sending
      (is (= ts (:ts item)) "Item should have been time-stamped")
      (is (= 9 (:time item)) "Time should have been assigned")
      (doseq [other (dissoc result tab-key)]
        (is (= (val other) (get (:url-times test-db) (key other))) "Other items should have remained untouched")
        )
      ))
  (testing "Add zero seconds"
    (let [tab     {:url   "http://numergent.com/about/"
                   :title "About Numergent"}
          ts      1445964037799
          result  (data/track-url-time (:url-times test-db)
                                       tab
                                       0
                                       ts)
          tab-key (utils/url-key "http://numergent.com/about/")
          item    (get result tab-key)]
      (is result)
      (is (nil? item) "We should not have an item with that URL")
      (is (= 7 (count result)) "We should not have added any elements")
      (is (= result (:url-times test-db)))

      ))
  (testing "Non-http URLs aren't tracked"
    (let [tab     {:url   "file:///Users/my-user/the-file.html"
                   :title "Numergent limited"}
          ts      1445964037799
          result  (data/track-url-time (:url-times test-db)
                                       tab
                                       9
                                       ts)
          tab-key (utils/url-key "file:///Users/my-user/the-file.html")
          item    (get result tab-key)]
      (is result)
      (is (= 7 (count result)) "We shouldn't have added an element")
      (is (nil? item) "We should not have the URL on our list")
      (is (= result (:url-times test-db)) "Database should not have been altered")
      ))
  (testing "Add time to a new tab on an empty set"
    (let [tab     {:url        "http://numergent.com/"
                   :title      "Numergent limited"
                   :favIconUrl "http://numergent.com/favicon.png"}
          ts      12345
          result  (data/track-url-time {} tab 9 ts)
          tab-key (utils/url-key "http://numergent.com/")
          item    (get result tab-key)]
      (is result)
      (is (= 1 (count result)) "We should have an extra element")
      (is item)
      (are [k] (= (k item) (k tab)) :url :title)                      ; All the keys should have been updated from the record we're sending
      (is (= ts (:ts item)) "Item should have been time-stamped")
      (is (= 9 (:time item)) "Time should have increased")))
  (testing "Attempting to add time to an invalid URL causes no changes"
    (let [tab    {:url   nil
                  :title "Numergent limited"
                  :icon  "http://numergent.com/favicon.png"}
          ts     12345
          result (data/track-url-time (:url-times test-db) tab 9 ts)]
      (is (= result (:url-times test-db))))
    (let [tab    {:url ""}
          ts     12345
          result (data/track-url-time (:url-times test-db) tab 9 ts)]
      (is (= result (:url-times test-db)))
      ))
  (testing "Attempting to add time to an ignored URL causes no changes"
    ;; Repeating almost the exact same test as when we tracked the time for
    ;; Numergent, only passing it as an ignore domain now.
    (let [tab         {:url        "http://numergent.com/"
                       :title      "Numergent limited"
                       :favIconUrl "http://numergent.com/favicon.png"}
          ts          1445964037799
          with-ignore (data/track-url-time (:url-times test-db) tab 9 ts
                                           :ignore-set #{"localhost" "somedomain.com" "numergent.com"})
          no-ignore   (data/track-url-time (:url-times test-db) tab 9 ts
                                           :ignore-set #{"localhost" "somedomain.com"})
          tab-key     (utils/url-key "http://numergent.com/")
          item        (get with-ignore tab-key)]
      (is with-ignore)
      (is (nil? item))
      (is (= with-ignore (:url-times test-db)) "URL times should not have been altered")
      (is (not= with-ignore no-ignore) "Removing the domain from the ignore list should result on the element being added")
      (is (= (count no-ignore) (inc (count with-ignore))) "Result without ignoring the element should have one more value")
      )
    )
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
                             (:favIconUrl tab) (:icon item)
                             "numergent.com" (:host item)
                             ts (:ts item)
                             1234 (:time item))
      ;; Let's make sure we did not break anything while adding an ignore parameter
      (is (= result (data/track-site-time {} tab 1234 ts :ignore-set #{"localhost" "newtab"}))
          "The result should be the same even if we pass an ignore-set")
      )
    )
  (testing "Add time to an existing database for an existing site"
    (let [tab    {:url        "http://numergent.com/opensource/index.html"
                  :title      "Further open source project details"
                  :favIconUrl "http://numergent.com/newfavicon.png"}
          ts     1445964037900
          result (data/track-site-time (:site-times test-db)
                                       tab
                                       3
                                       ts)
          id     (utils/host-key (utils/hostname "http://numergent.com/opensource/"))
          item   (get result id)]
      (is result)
      (is (= 6 (count result)) "We should get back the same number of sites")
      (is item)
      (are [expected result] (= expected result)
                             (:favIconUrl tab) (:icon item)
                             "numergent.com" (:host item)
                             ts (:ts item)
                             147 (:time item))
      (doseq [other (dissoc result id)]
        (is (= (val other) (get (:site-times test-db) (key other))) "Other items should have remained untouched")
        )
      ;; Let's make sure we did not break anything while adding an ignore parameter
      (is (= result (data/track-site-time (:site-times test-db) tab 3 ts
                                          :ignore-set #{"localhost" "newtab"}))
          "The result should be the same even if we pass an ignore-set")
      ))
  (testing "Add time to an existing database for a new site"
    (let [tab    {:url        "https://twitter.com/ArgesRic"
                  :title      "ArgesRic"
                  :favIconUrl "https://abs.twimg.com/favicons/favicon.ico"}
          ts     1445964037920
          result (data/track-site-time (:site-times test-db)
                                       tab
                                       9
                                       ts)
          id     (utils/host-key (utils/hostname "https://twitter.com/EvenSomeOtherUrl"))
          item   (get result id)]
      (is result)
      (is (= 7 (count result)) "Site count should have increased")
      (is item)
      (are [expected result] (= expected result)
                             (:favIconUrl tab) (:icon item)
                             "twitter.com" (:host item)
                             ts (:ts item)
                             9 (:time item))
      (doseq [other (dissoc result id)]
        (is (= (val other) (get (:site-times test-db) (key other))) "Other items should have remained untouched")
        )
      ;; Then, let's make sure we did not break anything while adding an ignore parameter
      (is (= result
             (data/track-site-time (:site-times test-db) tab 9 ts
                                   :ignore-set #{"localhost" "somedomain.com"})))
      )

    )
  (testing "Add zero time should not result on any changes"
    (let [tab    {:url        "https://twitter.com/ArgesRic"
                  :title      "ArgesRic"
                  :favIconUrl "https://abs.twimg.com/favicons/favicon.ico"}
          ts     1445964037920
          result (data/track-site-time (:site-times test-db)
                                       tab
                                       0
                                       ts)
          id     (utils/host-key (utils/hostname "https://twitter.com/ArgesRic"))
          item   (get result id)]
      (is result)
      (is (= result (:site-times test-db)))
      (is (nil? item)))
    )
  (testing "Add time from an invalid tab does not change the databse"
    (let [tab    {}
          ts     1445964037920
          result (data/track-site-time (:site-times test-db)
                                       tab
                                       9
                                       ts)]
      (is result)
      (is (= result (:site-times test-db)))))
  (testing "Add time to an ignored site does not change the database"
    ;; Repeating almost the exact same test as when we tracked the time for
    ;; Numergent, only passing it as an ignore domain now.
    (let [tab         {:url        "http://numergent.com/opensource/index.html"
                       :title      "Further open source project details"
                       :favIconUrl "http://numergent.com/newfavicon.png"}
          ts          1445964037900
          with-ignore (data/track-site-time (:site-times test-db) tab 3 ts
                                            :ignore-set #{"localhost" "somedomain.com" "numergent.com"})
          no-ignore   (data/track-site-time (:site-times test-db) tab 3 ts
                                            :ignore-set #{"localhost" "somedomain.com"})
          id          (utils/host-key (utils/hostname "http://numergent.com/opensource/"))
          item        (get no-ignore id)]
      (is with-ignore)
      (is (= with-ignore (:site-times test-db)))
      (is (not= with-ignore no-ignore))
      ;; Then let's verify the values on the one we actually added
      (are [expected result] (= expected result)
                             (:favIconUrl tab) (:icon item)
                             "numergent.com" (:host item)
                             ts (:ts item)
                             147 (:time item))))
  )


(deftest test-clean-up-by-time
  (testing "Clean up date and minimum time are respected"
    (let [min-date 1446028215913
          pruned   (data/clean-up-by-time (:url-times test-db) min-date 30)]
      (is pruned)
      (is (= 5 (count pruned)))
      ;; We removed the right elements
      (is (nil? (get pruned (utils/url-key "http://splunk.com/"))))
      (is (nil? (get pruned (utils/url-key "http://numergent.com/opensource/"))))
      ;; The very briefly accessed prismatic login remains because of its timestamp
      (is (get pruned (utils/url-key "http://getprismatic.com/oauth-complete?result=login")))
      ))
  (testing "Timestamp filtering is only on strictly greater than"
    (let [min-date 1446114615912
          pruned   (data/clean-up-by-time (:url-times test-db) min-date 30)]
      (is pruned)
      (is (= 3 (count pruned)))
      ;; getprismatic is still there
      (is (get pruned (utils/url-key "http://getprismatic.com/oauth-complete?result=login")))
      ;; ... as are all the keys we should have
      (is (= #{-327774960 -327358142 1609181525}
             (into #{} (keys pruned))))
      ))
  (testing "Cut-off seconds are respected when filtering"
    (let [min-date 1446114615912
          pruned   (data/clean-up-by-time (:url-times test-db) min-date 28)]
      (is pruned)
      (is (= 4 (count pruned)))
      ;; getprismatic is still there
      (is (get pruned (utils/url-key "http://getprismatic.com/oauth-complete?result=login")))
      ;; ... and we didn' lose splunk
      (is (get pruned (utils/url-key "http://splunk.com/"))))
    (let [min-date 1446114615913
          pruned   (data/clean-up-by-time (:url-times test-db) min-date 50)]
      (is pruned)
      (is (= 2 (count pruned)))
      (is (= #{-327774960 -327358142}
             (into #{} (keys pruned)))))
    ))

(deftest test-clean-up-ignored
  (let [url-times (:url-times test-db)]
    (is (= url-times
           (data/clean-up-ignored url-times #{}))
        "Passing an empty set should not change things")
    (is (= url-times
           (data/clean-up-ignored url-times #{"localhost" "somedomain.com"}))
        "Passing a set of not-matching domain does not change things")
    ;; Test removing a domain
    (let [result (data/clean-up-ignored url-times #{"localhost" "numergent.com"})]
      (is (= result (dissoc url-times -327774960 -526558523))
          "We should have removed the numergent-associated urls")
      (is (= 5 (count result))))
    ;; Test removing multiple domains
    (let [result (data/clean-up-ignored url-times #{"localhost" "getprismatic.com" "numergent.com"})]
      (is (= result (dissoc url-times -327774960 -526558523 1609181525))
          "We should have removed the numergent-associated urls")
      (is (= 4 (count result))))
    ))


(deftest test-accumulate-site-times
  (testing "Accumulate site times creates a total but doesn't add favicons"
    (is (= (into {} (map #(vector (key %) (assoc (val %) :icon nil))
                         (:site-times test-db)))
           (data/accumulate-site-times (:url-times test-db)))))
  (testing "Accumulate site times should disregard empty hosts"
    (let [data {2080624698
                {:url   "/tags/khroma/"
                 :time  117
                 :ts    1445964037798
                 :title "Khroma articles"}
                -526558523
                {:url   "http://numergent.com/opensource/"
                 :time  27
                 :ts    1445964037798
                 :title "Open source projects"}
                -327774960
                {:url   "http://numergent.com/tags/khroma/"
                 :time  12
                 :ts    1445964037798
                 :title "Khroma articles"}
                1917381154
                {:url   "http://www.kitco.com/market/"
                 :time  4
                 :ts    1446051494575
                 :title "New York spot price Gold..."
                 }}
          acc  (data/accumulate-site-times data)]
      ;; There should be no empty hostnames
      (is (nil? (get acc 0)))
      ;; Let' verify we got the right data
      (is (= {971841386  {:time 39, :icon nil, :host "numergent.com"},
              -915908674 {:time 4, :icon nil, :host "www.kitco.com"}}
             acc))
      ))
  )


(deftest test-accumulate-after-clean-up
  (testing "We get a value accumulation per site time after clean up"
    (let [min-date   1446114615912
          pruned     (data/clean-up-by-time (:url-times test-db) min-date 30)
          site-times (data/accumulate-site-times pruned)]
      (is pruned)
      (is (= {971841386  {:icon nil
                          :time 117
                          :host "numergent.com"}
              -331299663 {:icon nil
                          :time 901
                          :host "developer.chrome.com"}
              1366860619 {:icon nil
                          :time 3
                          :host "getprismatic.com"}}
             site-times))
      )))