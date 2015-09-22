(ns tabler.handler
  (:require [khroma.log :as console]
            [khroma.browser :as browser]
            [khroma.tabs :as tabs]
            [khroma.extension :as ext]
            [khroma.windows :as windows]
            [cljs.core.async :refer [>! <!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn init []
  (console/log "tabler.handler.init")
  (let [conns (browser/on-clicked)]
    (go (while true
          (let [tab (<! conns)]
            (console/log "On click handler. Tab: " tab)
            (console/log "Listing tabs for the current window...")
            (tabs/create {:url (str (ext/get-url "/" ) "index.html")})
            (doseq [t (.-tabs (<! (windows/get-current)))]
              (console/log "Tab: " t)))
          ))))
