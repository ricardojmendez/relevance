(defproject relevance-chrome "1.0.7"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.229"]
                 [org.clojure/core.async "0.2.391"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [cljsjs/react-bootstrap "0.30.2-0" :exclusions [org.webjars.bower/jquery]]
                 [khroma "0.3.0"]
                 [prismatic/dommy "1.1.0"]
                 [re-frame "0.8.0" :exclusions [cljsjs/react]]]

  :source-paths ["src/ui" "src/common" "src/background" "src/content"]
  :test-paths ["test"]

  :plugins [[lein-cljsbuild "1.1.3"]
            [lein-chromebuild "0.3.1"]
            [lein-doo "0.1.6"]]


  :doo {:build "test"}

  :cljsbuild {:builds
              {:background
               {:source-paths ["src/background" "src/common"]
                :compiler     {:output-to     "target/unpacked/background.js"
                               :output-dir    "target/js/background"
                               :main          "relevance.background"
                               :optimizations :whitespace
                               :pretty-print  true}}
               :content
               {:source-paths ["src/content" "src/common"]
                :compiler     {:output-to     "target/unpacked/content.js"
                               :output-dir    "target/js/content"
                               :main          "relevance.startpage"
                               :optimizations :whitespace
                               :pretty-print  true}}
               :ui
               {:source-paths ["src/ui" "src/common"]
                :compiler     {:output-to     "target/unpacked/ui.js"
                               :output-dir    "target/js/ui"
                               :main          "relevance.display"
                               :optimizations :whitespace
                               :pretty-print  true}}}}


  :chromebuild {:resource-paths   ["resources/js"
                                   "resources/dashboard"
                                   "resources/images"
                                   "resources/css"]
                :preserve-folders true
                :target-path      "target/unpacked"}

  :profiles {:release
             {:cljsbuild
              {:builds
               {:background {:compiler {:optimizations :advanced
                                        :pretty-print  false}}
                :content    {:compiler {:optimizations :advanced
                                        :pretty-print  false}}
                :ui         {:compiler {:optimizations :advanced
                                        :pretty-print  false}}}}}
             :test
             {:dependencies [[lein-doo "0.1.7"]]
              :cljsbuild
                            {:builds
                             {:test
                              {:source-paths ["test" "src/common"]
                               :compiler     {:output-to     "target/js/test/relevance-tests.js"
                                              :output-dir    "target/js/test"
                                              :main          relevance.test.runner
                                              :optimizations :none
                                              :pretty-print  :true}}}}}})
