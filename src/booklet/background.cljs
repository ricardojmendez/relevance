(ns booklet.background
  (:require [cljs.core.async :refer [>! <!]]
            [khroma.log :as console]
            [khroma.runtime :as runtime]
            [khroma.windows :as windows])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn init []
  (go (let [conns   (runtime/on-connect)
            content (<! conns)]
        (console/log "On background. Got message: " (<! content))
        (>! content "Hello from background")
        (init))))