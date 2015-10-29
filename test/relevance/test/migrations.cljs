(ns relevance.test.migrations
  (:require [cljs.test :refer-macros [deftest testing is]]
            [relevance.utils :as utils]
            ))

(def initial-test-data
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
  (doseq [[k v] (:url-times initial-test-data)]
    (is (= k (utils/key-from-url (:url v))) (str "URL " (:url v)))))


