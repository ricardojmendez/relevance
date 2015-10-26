(ns booklet.content
  (:require [booklet.utils :refer [on-channel from-transit to-transit]]
            [dommy.core :refer-macros [sel sel1] :as dommy]
            [khroma.runtime :as runtime]
            [khroma.log :as console]
            [cljs.core.async :refer [>! <!]]
            [khroma.storage :as storage])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn transform-node [database node]
  (let [parent (.-parentNode node)
        href   (.-href parent)
        id     (.hashCode href)
        data   (get database id)]
    (when data
      (aset node "textContent"
            (str (aget node "textContent") " [time viewed: " (:time data) " ms]"))
      )
    )
  )

(defn do-transformations []
  (go
    (let [data (from-transit (:data (<! (storage/get))))]
      (doseq [node (sel :.result_url_heading)]
        (transform-node (:url-times data) node)
        #_(aset node "textContent"
                (str
                  (aget node "textContent")
                  " - Hi there: "
                  (.-href (.-parentNode node)))))))
  (console/log (sel :.result_url_heading))
  (console/log (map dommy/text (sel :.result_url_heading)))
  (console/log (map #(.-parentNode %) (sel :.result_url_heading))))

(defn ^:export init []
  (console/log "Init on content script!")
  (do-transformations)
  (let [bg (runtime/connect)]
    (go
      (>! bg :content-initialized)
      (console/log "<-- Background replied" (<! bg)))))