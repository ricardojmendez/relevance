#!/usr/local/bin/planck
(ns relevance.generate-icons
  (:require [planck.core :refer [exit *command-line-args*]]
            [planck.shell :refer [sh]]))

(defn ensure-succeeded! [x]
      (print (:out x))
      (when-not (zero? (:exit x))
                (println "Non-zero exit code!")
                (print (:err x))
                (exit (:exit x))))


(defn do! [& args]
      (ensure-succeeded! (apply sh args)))

(def src-path "resources/")
(def dst-path "resources/images/")
(def sizes [16 19 38 48 128])

(defn do-resize! [filename]
      (doseq [size sizes]
             (let [origin (str src-path filename)
                   target (str dst-path "icon" size ".png")]
                  (println "Converting" origin "to" target)
                  (do! "convert" origin "-resize" (str size "x" size) target)
                  )))


(let [filename (first *command-line-args*)]
     (if (empty? filename)
       (println "Need a file name for the image to proceed. Aborting.")
       (do-resize! filename)))

(println "Done")