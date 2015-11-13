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
               93819220 "about:blank"
               -847465478 "file:///Users/ricardo/Sources/user.html"
               ))

(deftest test-host-key
  (are [k url] (= k (utils/host-key url))
               -702889725 "www.google.com"
               -702889725 "www.Google.com"
               971841386 "numergent.com"
               -292940973 "www.numergent.com"
               0 ""
               0 "   "
               0 nil)
  )

(deftest test-time-display
  (are [time label] (= (utils/time-display time) label)
                    0 "< 1s"
                    0.999 "< 1s"
                    1 "1s"
                    3 "3s"
                    49 "49s"
                    60 "1min"
                    61 "1min 1s"
                    119 "1min 59s"
                    120 "2min"
                    124 "2min 4s"
                    762 "12min 42s"
                    3600 "1h"
                    3610 "1h"
                    3660 "1h 1min"
                    8659 "2h 24min"
                    86592 "1d"
                    124076 "1d 10h"
                    248996 "2d 21h"
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
                  "file:///Users/ricardo/Sources/user.html" ""
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