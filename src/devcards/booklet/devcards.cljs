(ns booklet.devcards
  (:require [devcards.core :as core]
            [khroma.log :as console]))


(defn ^:export main []
  (console/log "Initializing UI...")
  (core/start-devcard-ui!*))