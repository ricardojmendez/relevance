(defproject booklet-chrome "0.3.0-SNAPSHOT"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.145"]
                 [org.clojure/core.async "0.2.371"]
                 [com.cognitect/transit-cljs "0.8.225"]
                 [cljsjs/react-bootstrap "0.25.2-0" :exclusions [org.webjars.bower/jquery]]
                 [khroma "0.2.0-SNAPSHOT"]
                 [prismatic/dommy "1.1.0"]
                 [re-frame "0.4.1" :exclusions [cljsjs/react]]
                 ]
  :source-paths ["src/ui" "src/common" "src/background" "src/content"]

  :plugins [[lein-cljsbuild "1.1.0"]
            [lein-chromebuild "0.3.0"]]

  :cljsbuild {:builds
              {:background
               {:source-paths ["src/background" "src/common"]
                :compiler     {:output-to     "target/unpacked/background.js"
                               :output-dir    "target/js/background"
                               :main          "booklet.background"
                               :optimizations :whitespace
                               :pretty-print  true}}
               :content
               {:source-paths ["src/content" "src/common"]
                :compiler     {:output-to     "target/unpacked/content.js"
                               :output-dir    "target/js/content"
                               :main          "booklet.startpage"
                               :optimizations :whitespace
                               :pretty-print  true}}
               :ui
               {:source-paths ["src/ui" "src/common"]
                :compiler     {:output-to     "target/unpacked/ui.js"
                               :output-dir    "target/js/ui"
                               :main          "booklet.display"
                               :optimizations :whitespace
                               :pretty-print  true}}}

              }

  :chromebuild {:resource-paths ["resources/js"
                                 "resources/html"
                                 "resources/images"
                                 "resources/css"]
                :target-path    "target/unpacked"}

  :profiles {:release
             {:cljsbuild
              {:builds
               {:background {:compiler {:optimizations :advanced
                                        :pretty-print  false}}
                :content    {:compiler {:optimizations :advanced
                                        :pretty-print  false}}
                :ui         {:compiler {:optimizations :advanced
                                        :pretty-print  false}}}}

              }})
