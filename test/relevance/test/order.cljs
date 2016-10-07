(ns relevance.test.order
  (:require [cljs.test :refer-macros [deftest testing is are]]
            [relevance.order :as order]
            [relevance.utils :as utils]))


(deftest test-time-score
  ;; A score for an unknown URL with no tab index is zero
  (is (= 0 (order/time-score {:url "http://google.com"}
                             {}
                             {}
                             {})))
  ;; A score for an unknown URL with no tab index is the complement of its index
  (is (= -20 (order/time-score {:url   "http://google.com"
                                :index 20}
                               {}
                               {}
                               {})))
  ;; A score for a known URL equals its time value
  (is (= 291 (order/time-score {:url "http://google.com"}
                               {(utils/url-key "http://google.com")
                                {:time 291}}
                               {}
                               {})))
  ;; A score for a known URL equals its time value, even if it includes a port
  (is (= 291 (order/time-score {:url "http://google.com:80"}
                               {(utils/url-key "http://google.com")
                                {:time 291}}
                               {}
                               {})))
  ;; A score for a known URL gets added the time for its site
  (is (= 468 (order/time-score {:url "http://google.com/somepage"}
                               {(utils/url-key "http://google.com")          {:time 291}
                                (utils/url-key "http://google.com/somepage") {:time 345}}
                               {(utils/host-key "google.com") {:time 123}
                                (utils/host-key "apple.com")  {:time 987}}
                               {})))
  ;; A score for a known URL gets added the time for its host, even if it's using
  ;; a non-standard port
  (is (= 123 (order/time-score {:url "http://google.com:9090/somepage"}
                               {(utils/url-key "http://google.com")          {:time 291}
                                (utils/url-key "http://google.com/somepage") {:time 345}}
                               {(utils/host-key "google.com") {:time 123}
                                (utils/host-key "apple.com")  {:time 987}}
                               {})))
  (is (= 448 (order/time-score {:url "http://google.com:9090/somepage"}
                               {(utils/url-key "http://google.com")               {:time 291}
                                (utils/url-key "http://google.com/somepage")      {:time 345}
                                (utils/url-key "http://google.com:9090/somepage") {:time 325}}
                               {(utils/host-key "google.com") {:time 123}
                                (utils/host-key "apple.com")  {:time 987}}
                               {})))
  ;; A score for a known URL is not affected by the score of other URLs for the same site
  (is (= 414 (order/time-score {:url "http://google.com/"}
                               {(utils/url-key "http://google.com")          {:time 291}
                                (utils/url-key "http://google.com/somepage") {:time 345}}
                               {(utils/host-key "google.com") {:time 123}
                                (utils/host-key "apple.com")  {:time 987}}
                               {})))
  ;; A page inherits its site's score even if the page is unknown
  (is (= 987 (order/time-score {:url "http://apple.com/mac"}
                               {(utils/url-key "http://google.com")          {:time 291}
                                (utils/url-key "http://google.com/somepage") {:time 345}}
                               {(utils/host-key "google.com") {:time 123}
                                (utils/host-key "apple.com")  {:time 987}}
                               {})))
  ;; A page inherits its site's score even if the page is unknown, but
  ;; substracts the index so that they are placed at the end.
  (is (= 975 (order/time-score {:url "http://apple.com/mac" :index 12}
                               {(utils/url-key "http://google.com")          {:time 291}
                                (utils/url-key "http://google.com/somepage") {:time 345}}
                               {(utils/host-key "google.com") {:time 123}
                                (utils/host-key "apple.com")  {:time 987}}
                               {})))
  ;; A page that has sound gets no extra score if the :sound-to-left? key isn't on settings
  (is (= 291 (order/time-score {:url     "http://google.com"
                                :audible true}
                               {(utils/url-key "http://google.com")
                                {:time 291}}
                               {}
                               {})))
  ;; An audible page gets an extra score if the :sound-to-left? key is set to true on the settings,
  ;; but based on the index, not the time spent
  (is (= (+ 5 123 order/sound-extra-score)
         (order/time-score {:url     "http://google.com/translate"
                            :index   5
                            :audible true}
                           {(utils/url-key "http://google.com/translate")
                            {:time 456}}
                           {(utils/host-key "google.com") {:time 123}
                            (utils/host-key "apple.com")  {:time 987}}
                           {:sound-to-left? true})))
  ;; Non-http URLs are penalized, but get a minimum score of 1 (they will
  ;; likely have a time of 0 to begin with since they aren't tracked)
  (is (= (* 124 order/non-http-penalty)
         (order/time-score {:url   "chrome://google.com/translate"
                            :index 5}
                           {}
                           {(utils/host-key "google.com") {:time 123}
                            (utils/host-key "apple.com")  {:time 987}}
                           {}))))


(deftest test-score-tabs
  (let [url-times  {(utils/url-key "http://google.com")          {:time 2910}
                    (utils/url-key "http://google.com/somepage") {:time 345}
                    (utils/url-key "http://apple.com/osx")       {:time 10101}
                    (utils/url-key "http://apple.com/")          {:time 2120}}
        site-times {(utils/host-key "google.com") {:time 4295} ; Includes time from tabs which we have deleted
                    (utils/host-key "apple.com")  {:time 12221}}]
    ;; We have spent the longest at Apple, so it gets prioritized
    ;; The extension tab ends up at the end because it's not http
    (is (= [{:index 0 :id 23} {:index 1 :id 9} {:index 2 :id 1}]
           (order/score-tabs [{:url   "http://google.com"
                               :id    9
                               :index 15}
                              {:url   "chrome://extensions/"
                               :id    1
                               :index 1}
                              {:url   "https://apple.com/macbook"
                               :id    23
                               :index 912}]
                             url-times
                             site-times
                             {})))
    ;; An unknown page ends up at the end
    (is (= [{:index 0 :id 23} {:index 1 :id 9} {:index 2 :id 1} {:index 3 :id 2}]
           (order/score-tabs [{:url   "http://google.com"
                               :id    9
                               :index 15}
                              {:url   "http://youtube.com/"
                               :id    2
                               :index 27}
                              {:url   "chrome://extensions/"
                               :id    1
                               :index 1}
                              {:url   "https://apple.com/macbook"
                               :id    23
                               :index 912}]
                             url-times
                             site-times
                             {})))
    ;; Two unknown pages get sorted by their index order
    (is (= [{:index 0 :id 23} {:index 1 :id 9} {:index 2 :id 1} {:index 3 :id 123} {:index 4 :id 2}]
           (order/score-tabs [{:url   "http://google.com"
                               :id    9
                               :index 15}
                              {:url   "http://youtube.com/"
                               :id    2
                               :index 27}
                              {:url   "http://vimeo.com/"
                               :id    123
                               :index 26}
                              {:url   "chrome://extensions/"
                               :id    1
                               :index 1}
                              {:url   "https://apple.com/macbook"
                               :id    23
                               :index 912}]
                             url-times
                             site-times
                             {})))
    ;; An unknown page that is playing sound gets prioritized according to the settings
    (is (= [{:index 0 :id 2} {:index 1 :id 23} {:index 2 :id 9} {:index 3 :id 1}]
           (order/score-tabs [{:url   "http://google.com"
                               :id    9
                               :index 15}
                              {:url     "http://youtube.com/"
                               :id      2
                               :audible true
                               :index   27}
                              {:url   "chrome://extensions/"
                               :id    1
                               :index 1}
                              {:url   "https://apple.com/macbook"
                               :id    23
                               :index 912}]
                             url-times
                             site-times
                             {:sound-to-left? true})))
    ;; If for any reason the Apple URL is not http, it gets de-prioritized
    (is (= [{:index 0 :id 9} {:index 1 :id 1} {:index 2 :id 23}]
           (order/score-tabs [{:url   "http://google.com"
                               :id    9
                               :index 15}
                              {:url   "chrome://extensions/"
                               :id    1
                               :index 1}
                              {:url   "apple.com/macbook"
                               :id    23
                               :index 912}]
                             url-times
                             site-times
                             {})))
    )
  )
