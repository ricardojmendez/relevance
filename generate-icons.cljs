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

(def path "resources/images/")
(def sizes [16 19 38 48 128])


; TODO: Once we get planck 1.6, add command line args so that we can do either
; release or start

; (do! "git" "flow" "release" "start" timestamp)

(defn do-resize! [filename]
      (doseq [size sizes]
             (let [origin (str path filename)
                   target (str path "icon" size ".png")]
                  (println "Converting" origin "to" target)
                  (do! "convert" origin "-resize" (str size "x" size) target)
                  )))


(let [filename (first *command-line-args*)]
     (if (empty? filename)
       (println "Need a file name for the image to proceed. Aborting.")
       (do-resize! filename)))

(println "Done")