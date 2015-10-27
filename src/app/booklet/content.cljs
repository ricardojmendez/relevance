(ns booklet.content
  (:require [booklet.utils :refer [on-channel from-transit to-transit]]
            [dommy.core :refer-macros [sel sel1] :as dommy]
            [khroma.runtime :as runtime]
            [khroma.log :as console]
            [cljs.core.async :refer [>! <!]]
            [khroma.storage :as storage])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn transform-result-node!
  [database node]
  (let [parent    (.-parentNode node)
        href      (.-href parent)
        id        (hash-string href)
        data      (get database id)
        time      (:time data)
        root-item (-> parent .-parentNode .-parentNode .-parentNode)  ; Yeah, hacky as fuck
        ]
    (aset node "rootItem" root-item)
    (aset root-item "total-time" time)
    (when data
      (aset node "textContent"
            (str (aget node "textContent") " [time viewed: " time " ms]")))))

(defn do-transformations! []
  (go
    (let [data  (from-transit (:data (<! (storage/get))))
          nodes (sel :.result_url_heading)
          base  (sel1 :.web_regular_results)]
      (doseq [node nodes]
        (transform-result-node! (:url-times data) node))
      (console/log "Base" base)
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

(defn ^:export init []
  (console/log "Init on content script!")
  (do-transformations!)
  (let [bg (runtime/connect)]
    (go
      (>! bg :content-initialized)
      (console/log "<-- Background replied" (<! bg)))))