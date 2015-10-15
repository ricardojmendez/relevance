(defproject booklet-chrome "0.1.0"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [cljs-ajax "0.3.14"]
                 [cljsjs/react-bootstrap "0.25.1-0" :exclusions [org.webjars.bower/jquery]]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [khroma "0.1.0"]
                 [prismatic/dommy "1.1.0"]
                 [re-frame "0.4.1" :exclusions [cljsjs/react]]
                 ]
  :source-paths ["src"]
  :profiles {:dev {:plugins     [[lein-cljsbuild "1.1.0"]
                                 [lein-chromebuild "0.3.0"]]
                   :cljsbuild   {:builds {:main
                                          {:source-paths ["src"]
                                           :compiler     {:output-to       "target/unpacked/booklet.js"
                                                          :output-dir      "target/js"
                                                          :optimizations   :whitespace
                                                          :closure-defines {booklet.core/api-uri "http://localhost/from-the-config"}
                                                          ; :closure-defines {"booklet.core.api_uri" "http://localhost/from-the-config"}
                                                          :pretty-print    true}}}}

                   :chromebuild {:resource-paths ["resources/js"
                                                  "resources/html"
                                                  "resources/images"
                                                  "resources/css"]
                                 :target-path    "target/unpacked"}
                   }}) 
