(defproject booklet-chrome "0.2.0-SNAPSHOT"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.145"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [cljs-ajax "0.5.0"]
                 [cljsjs/react-bootstrap "0.25.2-0" :exclusions [org.webjars.bower/jquery]]
                 [khroma "0.2.0-SNAPSHOT"]
                 [prismatic/dommy "1.1.0"]
                 [re-frame "0.4.1" :exclusions [cljsjs/react]]
                 ]
  :source-paths ["src/app" "src/devcards"]
  :profiles {:dev {:plugins     [[lein-cljsbuild "1.1.0"]
                                 [lein-chromebuild "0.3.0"]]
                   :cljsbuild   {:builds
                                 {:dev
                                  {:source-paths ["src/app"]
                                   :compiler     {:output-to     "target/unpacked/booklet.js"
                                                  :output-dir    "target/js"
                                                  :externs       ["externs/misc-externs.js"]
                                                  :main          "booklet.background"
                                                  :optimizations :whitespace
                                                  :pretty-print  true}}}}

                   :chromebuild {:resource-paths ["resources/js"
                                                  "resources/html"
                                                  "resources/images"
                                                  "resources/css"]
                                 :target-path    "target/unpacked"}
                   }}) 
