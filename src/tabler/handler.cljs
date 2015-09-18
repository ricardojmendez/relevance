(ns tabler.handler
  (:require [khroma.log :as console]
            [khroma.browser :as browser]
            [khroma.tabs :as tabs]
            [khroma.windows :as windows]
            [cljs.core.async :refer [>! <!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn init []
  (console/log "tabler.handler.init")
  (let [conns (browser/on-click)]
    (go (while true
          (let [tab (<! conns)]
            (console/log "On click handler. Tab: " tab)
            (console/log "On window: " (<! (windows/get-current)))
            (console/log "Active tab:" (<! (tabs/get-active-tab))))
          ))))

(init)