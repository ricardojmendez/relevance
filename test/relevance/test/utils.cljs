(ns relevance.test.utils
  (:require [cljs.test :refer-macros [deftest testing is are]]
            [relevance.utils :as utils]
            ))


(deftest test-key-from-url
  (is (= (utils/key-from-url "http://localhost/")
         (utils/key-from-url "https://localhost/"))
      "Key should disregard protocol")
  (is (= (utils/key-from-url "https://localhost")
         (utils/key-from-url "https://localhost/"))
      "Key should disregard trailing slashes")
  (is (= (utils/key-from-url "https://localhost#hash")
         (utils/key-from-url "https://localhost/#hash"))
      "Key should disregard trailing hashtags")
  (is (= (utils/key-from-url "https://LOCALHOST")
         (utils/key-from-url "https://localhost"))
      "Key is not case-sensitive")
  (is (= (utils/key-from-url "https://LOCALHOST/someUrl#hash")
         (hash-string "localhost/someUrl"))
      "Our hash calculations are consistent with hash-string")
  (is (not= (utils/key-from-url "https://localhost?q=v")
            (utils/key-from-url "https://localhost?q="))
      "Key should respect query strings")
  (is (not= (utils/key-from-url "https://localhost.com/path")
            (utils/key-from-url "https://localhost.com/Path"))
      "Path is case-sensitive")
  ;; Let's confirm we actually return a consistent integer for some known values
  (are [k url] (= k (utils/key-from-url url))
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
