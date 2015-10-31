(ns relevance.test.utils
  (:require [cljs.test :refer-macros [deftest testing is are]]
            [relevance.utils :as utils]
            ))


(deftest test-key-from-url
  (is (= (utils/url-key "http://localhost/")
         (utils/url-key "https://localhost/"))
      "Key should disregard protocol")
  (is (= (utils/url-key "https://localhost")
         (utils/url-key "https://localhost/"))
      "Key should disregard trailing slashes")
  (is (= (utils/url-key "https://localhost#hash")
         (utils/url-key "https://localhost/#hash"))
      "Key should disregard trailing hashtags")
  (is (= (utils/url-key "https://LOCALHOST")
         (utils/url-key "https://localhost"))
      "Key is not case-sensitive")
  (is (= (utils/url-key "https://LOCALHOST/someUrl#hash")
         (hash-string "localhost/someUrl"))
      "Our hash calculations are consistent with hash-string")
  (is (not= (utils/url-key "https://localhost?q=v")
            (utils/url-key "https://localhost?q="))
      "Key should respect query strings")
  (is (not= (utils/url-key "https://localhost.com/path")
            (utils/url-key "https://localhost.com/Path"))
      "Path is case-sensitive")
  ;; Let's confirm we actually return a consistent integer for some known values
  (are [k url] (= k (utils/url-key url))
               -20650657 "https://LOCALHOST/someUrl#hash"
               -380467869 "http://google.com"
               -380467869 "https://google.com"
               -380467869 "https://google.com/"
               -314744948 "https://www.google.com"
               -314744948 "https://www.google.com/#hash"
               -596725840 "https://www.google.com/somePath?q=v"
               -327774960 "http://numergent.com/tags/khroma/"
               -526558523 "http://numergent.com/opensource/"
               )
  )

(deftest test-time-display
  (are [time label] (= (utils/time-display time) label)
                    500 "< 1s"
                    999 "< 1s"
                    1000 "1s"
                    1001 "1s"
                    1999 "1s"
                    3742 "3s"
                    49231 "49s"
                    124076 "2min 4s"
                    762661 "12min 42s"
                    8659266 "2h 24min"
                    86592666 "1d 0h"
                    124076042 "1d 10h"
                    248996042 "2d 21h"
                    ))

(deftest test-host
  (are [url name] (= (utils/hostname url) name)
                  "https://www.google.com/some?q=v" "www.google.com"
                  "https://www.Google.com/some?q=v" "www.google.com"
                  "https://WWW.GOOGLE.COM/some?q=v" "www.google.com"
                  "http://WWW.GOOGLE.COM/some?q=v" "www.google.com"
                  "https://GOOGLE.COM:443/some?q=v" "google.com"
                  "https://GOOGLE.COM:3000/some?q=v" "google.com"     ; host would have included the port
                  "http://numergent.com/tag/khroma" "numergent.com"
                  "about:blank" ""
                  "chrome://extensions/?id=okhigbflgnbihoiokilagelkalkcigfp" "extensions"
                  "chrome-extension://okhigbflgnbihoiokilagelkalkcigfp/index.html" "okhigbflgnbihoiokilagelkalkcigfp"
                  "" ""
                  nil nil
                  ))


(deftest test-protocol
  (are [url name] (= (utils/protocol url) name)
                  "https://www.google.com" "https:"
                  "http://www.Google.com" "http:"
                  "HTTP://www.numergent.com" "http:"
                  "HTTPS://www.numergent.com" "https:"
                  "about:blank" "about:"
                  "chrome://extensions/?id=okhigbflgnbihoiokilagelkalkcigfp" "chrome:"
                  "chrome-extension://okhigbflgnbihoiokilagelkalkcigfp/index.html" "chrome-extension:"
                  "" "file:"
                  nil nil
                  ))


(deftest test-is-http
  (are [url result] (= (utils/is-http? url) result)
                    "http:" true
                    "https:" true
                    "HTTP:" true
                    "HTTPS:" true
                    "http://localhost" true
                    "https://numergent.com/" true
                    "chrome://extensions/?id=okhigbflgnbihoiokilagelkalkcigfp" false
                    "http" false
                    "" false
                    nil false
                    ))