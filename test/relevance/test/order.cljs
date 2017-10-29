(ns relevance.test.order
  (:require [cljs.test :refer-macros [deftest testing is are]]
            [relevance.order :as order]
            [relevance.utils :as utils]))


(deftest test-time-score
  ;; A score for an unknown URL with no tab index is zero
  (is (= {:score 0 :priority? false}
         (order/time-score {:url "http://google.com"}
                           {}
                           {}
                           {})))
  ;; A score for an unknown URL with no site time is 10% its index
  (is (= {:score 2 :priority? false}
         (order/time-score {:url   "http://google.com"
                            :index 20}
                           {}
                           {}
                           {})))
  ;; A score for an unknown URL with a known site time gets 10% of its index
  ;; added to the domain time
  (is (= {:score 125 :priority? false}
         (order/time-score {:url   "http://google.com/about"
                            :index 20}
                           {}
                           {(utils/host-key "google.com") {:time 123}
                            (utils/host-key "apple.com")  {:time 987}}
                           {})))
  ;; A score for a known URL equals its time value
  (is (= {:score 291 :priority? false}
         (order/time-score {:url "http://google.com"}
                           {(utils/url-key "http://google.com")
                            {:time 291}}
                           {}
                           {})))
  ;; A score for a known URL equals its time value, even if it includes a port
  (is (= {:score 291 :priority? false}
         (order/time-score {:url "http://google.com:80"}
                           {(utils/url-key "http://google.com")
                            {:time 291}}
                           {}
                           {})))
  ;; A score for a known URL gets added the time for its site
  (is (= {:score 468 :priority? false}
         (order/time-score {:url "http://google.com/somepage"}
                           {(utils/url-key "http://google.com")          {:time 291}
                            (utils/url-key "http://google.com/somepage") {:time 345}}
                           {(utils/host-key "google.com") {:time 123}
                            (utils/host-key "apple.com")  {:time 987}}
                           {})))
  ;; A score for a known URL gets added the time for its host, even if it's using
  ;; a non-standard port
  (is (= {:score 123 :priority? false}
         (order/time-score {:url "http://google.com:9090/somepage"}
                           {(utils/url-key "http://google.com")          {:time 291}
                            (utils/url-key "http://google.com/somepage") {:time 345}}
                           {(utils/host-key "google.com") {:time 123}
                            (utils/host-key "apple.com")  {:time 987}}
                           {})))
  (is (= {:score 448 :priority? false}
         (order/time-score {:url "http://google.com:9090/somepage"}
                           {(utils/url-key "http://google.com")               {:time 291}
                            (utils/url-key "http://google.com/somepage")      {:time 345}
                            (utils/url-key "http://google.com:9090/somepage") {:time 325}}
                           {(utils/host-key "google.com") {:time 123}
                            (utils/host-key "apple.com")  {:time 987}}
                           {})))
  ;; A score for a known URL is not affected by the score of other URLs for the same site
  (is (= {:score 414 :priority? false}
         (order/time-score {:url "http://google.com/"}
                           {(utils/url-key "http://google.com")          {:time 291}
                            (utils/url-key "http://google.com/somepage") {:time 345}}
                           {(utils/host-key "google.com") {:time 123}
                            (utils/host-key "apple.com")  {:time 987}}
                           {})))
  ;; When there are no known URLs 
  ;; A page inherits its site's score even if the page is unknown
  (is (= {:score 987 :priority? false}
         (order/time-score {:url "http://apple.com/mac"}
                           {(utils/url-key "http://google.com")          {:time 291}
                            (utils/url-key "http://google.com/somepage") {:time 345}}
                           {(utils/host-key "google.com") {:time 123}
                            (utils/host-key "apple.com")  {:time 987}}
                           {})))
  ;; A page inherits its site's score even if the page is unknown, but
  ;; adds a percent of its index so that they are placed at the end.
  (is (= {:score 990 :priority? false}
         (order/time-score {:url "http://apple.com/mac" :index 30}
                           {(utils/url-key "http://google.com")          {:time 291}
                            (utils/url-key "http://google.com/somepage") {:time 345}}
                           {(utils/host-key "google.com") {:time 123}
                            (utils/host-key "apple.com")  {:time 987}}
                           {})))
  ;; A page that has sound gets no extra score if the :sound-to-left? key isn't on settings
  (is (= {:score 291 :priority? false}
         (order/time-score {:url     "http://google.com"
                            :audible true}
                           {(utils/url-key "http://google.com")
                            {:time 291}}
                           {}
                           {})))
  ;; An audible page gets an extra score if the :sound-to-left? key is set to true on the settings,
  ;; but based on the index, not the time spent
  (is (= {:score     (+ 5 123 order/sound-extra-score)
          :priority? true}
         (order/time-score {:url     "http://google.com/translate"
                            :index   5
                            :audible true}
                           {(utils/url-key "http://google.com/translate")
                            {:time 456}}
                           {(utils/host-key "google.com") {:time 123}
                            (utils/host-key "apple.com")  {:time 987}}
                           {:sound-to-left? true})))
  ;; Non-http URLs are penalized, using their index as the score even if they had time tracked
  (is (= {:score     (* (+ 123 5) order/non-http-penalty)
          :priority? false}
         (order/time-score {:url   "chrome://google.com/translate"
                            :index 5}
                           {(utils/url-key "chrome://google.com/translate")
                            {:time 9001}}
                           {(utils/host-key "google.com") {:time 123}
                            (utils/host-key "apple.com")  {:time 987}}
                           {}))))


(deftest test-score-tabs
  (let [url-times  {(utils/url-key "http://google.com")          {:time 2910}
                    (utils/url-key "http://google.com/somepage") {:time 345}
                    (utils/url-key "http://apple.com/osx")       {:time 10101}
                    (utils/url-key "http://apple.com/")          {:time 2120}}
        site-times {(utils/host-key "google.com")        {:time 4295 :host "google.com"} ; Includes time from tabs which we have deleted
                    (utils/host-key "apple.com")         {:time 12221 :host "apple.com"}
                    (utils/host-key "support.apple.com") {:time 90 :host "support.apple.com"}}]
    ;; We have spent the longest at Apple, so it gets prioritized
    ;; The extension tab ends up at the end because it's not http
    (testing "Basic prioritization"
      (is (= [{:index 0 :id 23} {:index 1 :id 9} {:index 2 :id 1}]
             (order/sort-by-root [{:url   "http://google.com"
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
                                 {}))))
    ;; Even though we have not spent any time at the support.apple.com page,
    ;; it will get prioritized right after apple.com because they share a root domain
    (testing "Pages get prioritized based on the root domain"
      (is (= [{:index 0 :id 23} {:index 1 :id 3} {:index 2 :id 9} {:index 3 :id 1}]
             (order/sort-by-root [{:url   "http://google.com"
                                   :id    9
                                   :index 15}
                                  {:url   "chrome://extensions/"
                                   :id    1
                                   :index 1}
                                  {:url   "https://apple.com/macbook"
                                   :id    23
                                   :index 912}
                                  {:url   "https://support.apple.com/en-us/"
                                   :id    3
                                   :index 0}]
                                 url-times
                                 site-times
                                 {}))))
    ;; All pages on the apple domain will end up together, even if one isn't known
    (is (= [{:index 0 :id 23} {:index 1 :id 3} {:index 2 :id 300} {:index 3 :id 9} {:index 4 :id 1}]
           (order/sort-by-root [{:url   "http://google.com"
                                 :id    9
                                 :index 15}
                                {:url   "chrome://extensions/"
                                 :id    1
                                 :index 1}
                                {:url   "https://apple.com/macbook"
                                 :id    23
                                 :index 912}
                                {:url   "https://support.apple.com/en-us/"
                                 :id    3
                                 :index 0}
                                {:url   "https://icloud.apple.com/"
                                 :id    300
                                 :index 91}]
                               url-times
                               site-times
                               {})))
    (testing "All pages on the apple domain will end up together, even if no pages whatsoever are known"
      (is (= [{:index 0 :id 23} {:index 1 :id 3} {:index 2 :id 300} {:index 3 :id 9} {:index 4 :id 1}]
             (order/sort-by-root [{:url   "http://google.com"
                                   :id    9
                                   :index 15}
                                  {:url   "chrome://extensions/"
                                   :id    1
                                   :index 1}
                                  {:url   "https://apple.com/macbook"
                                   :id    23
                                   :index 912}
                                  {:url   "https://support.apple.com/en-us/"
                                   :id    3
                                   :index 0}
                                  {:url   "https://icloud.apple.com/"
                                   :id    300
                                   :index 91}]
                                 {}
                                 site-times
                                 {}))))
    (testing "An unknown page ends up at the end"
      (is (= [{:index 0 :id 23} {:index 1 :id 9} {:index 2 :id 2} {:index 3 :id 1}]
             (order/sort-by-root [{:url   "http://google.com"
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
                                 {}))))
    (testing "Two unknown pages get sorted by their index order, but a penalized page ends up at the end"
      (is (= [{:index 0 :id 23} {:index 1 :id 9} {:index 2 :id 2} {:index 3 :id 123} {:index 4 :id 1}]
             (order/sort-by-root [{:url   "http://google.com"
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
                                 {}))))
    (testing "An unknown page that is playing sound gets prioritized according to the settings"
      (is (= [{:index 0 :id 2} {:index 1 :id 23} {:index 2 :id 9} {:index 3 :id 1}]
             (order/sort-by-root [{:url   "http://google.com"
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
                                 {:sound-to-left? true}))))
    (testing "If for any reason the Apple URL is not http, it gets de-prioritized"
      (is (= [{:index 0 :id 9} {:index 1 :id 23} {:index 2 :id 1}]
             (order/sort-by-root [{:url   "http://google.com"
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
                                 {}))))
    )
  )
