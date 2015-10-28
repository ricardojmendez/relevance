(ns booklet.startpage
  (:require [booklet.utils :refer [from-transit key-from-url]]
            [dommy.core :refer-macros [sel sel1] :as dommy]
            [khroma.runtime :as runtime]
            [khroma.log :as console]
            [cljs.core.async :refer [>! <!]]
            [khroma.storage :as storage])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn create-node [tag color text]
  (-> (dommy/create-element tag)
      (dommy/set-style! :color color)
      (dommy/set-text! text)))

(defn transform-result-node!
  [database node]
  (let [parent    (.-parentNode node)
        href      (.-href parent)
        id        (key-from-url href)
        data      (get database id)
        time      (:time data)
        root-item (-> parent .-parentNode .-parentNode .-parentNode)  ; Yeah, hacky as fuck
        ]
    (aset node "rootItem" root-item)
    (aset root-item "total-time" time)
    (when data
      (doto node
        (dommy/append! (create-node :span "rgb(80, 99, 152)" " [time viewed: "))
        (dommy/append! (create-node :span "rgb(140, 101, 153)" (str time " ms")))
        (dommy/append! (create-node :span "rgb(80, 99, 152)" "]")))
      )))

(defn do-transformations! []
  (go
    (let [data  (from-transit (:data (<! (storage/get))))
          nodes (sel :.result_url_heading)
          base  (sel1 :.web_regular_results)]
      (doseq [node nodes]
        (transform-result-node! (:url-times data) node))
      (doall
        (->>
          nodes
          (map #(aget % "rootItem"))
          (map-indexed #(do
                         ;; We assign it (- 100 %1) so that the first nodes get a higher value
                         (aset %2 "sort-order" (or (aget %2 "total-time") (- 100 %1)))
                         %2))
          (sort-by #(* -1 (aget % "sort-order")))
          (map #(.appendChild base %))))

      )))

(defn ^:export main []
  (console/log "Init on content script!")
  (do-transformations!)
  (let [bg (runtime/connect)]
    (go
      (>! bg :content-initialized)
      (console/log "<-- Background replied" (<! bg)))))

(main)