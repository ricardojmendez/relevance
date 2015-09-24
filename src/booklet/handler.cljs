(ns booklet.handler
  (:require [khroma.log :as console]
            [khroma.browser :as browser]
            [khroma.tabs :as tabs]
            [khroma.extension :as ext]
            [khroma.windows :as windows]
            [cljs.core.async :refer [>! <!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn init []
  (console/log "booklet.handler.init")
  (let [conns (browser/on-clicked)]
    (go (while true
          (let [tab (<! conns)]
            (console/log "On click handler. Tab: " tab)
            ; TODO: Open only if we don't have one on the current window
            (tabs/create {:url (str (ext/get-url "/" ) "index.html")}))
          ))))
