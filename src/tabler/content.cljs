(ns tabler.content
  (:require [khroma.runtime :as runtime]
            [khroma.log :as console]
            [khroma.tabs :as tabs]
            [cljs.core.async :refer [>! <!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn init []
  (let [bg (runtime/connect)]
    (go (>! bg :lol-i-am-a-content-script)
        (console/log "On content")
        (console/log "Background said: " (<! bg)))))
