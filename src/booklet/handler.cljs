(ns booklet.handler
  (:require [khroma.log :as console]
            [khroma.browser-action :as browser]
            [khroma.tabs :as tabs]
            [khroma.extension :as ext]
            [khroma.windows :as windows]
            [cljs.core.async :refer [>! <!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn init []
  (console/log "booklet.handler.init")
  (go-loop
    [channel (browser/on-clicked)]
    (when (<! channel)
      (let [ext-url (str (ext/get-url "/") "index.html")
            ;; We could just get the window-id from the tab, but that still
            ;; requires us to make an extra call for the other tabs
            window  (<! (windows/get-current))
            our-tab (first (filter #(= ext-url (:url %)) (:tabs window)))]
        (if our-tab
          (tabs/activate (:id our-tab))
          (tabs/create {:url ext-url}))))
    (recur channel)
    ))
