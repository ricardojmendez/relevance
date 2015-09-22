(ns tabler.core
  (:require [cljs.core.async :refer [>! <!]]
            [clojure.walk :as walk]
            [khroma.runtime :as runtime]
            [khroma.log :as console]
            [khroma.storage :as storage]
            [khroma.tabs :as tabs]
            [reagent.core :as reagent]
            [re-frame.core :refer [dispatch register-sub register-handler subscribe dispatch-sync]]
            [khroma.windows :as windows])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))


;;;;------------------------------
;;;; Queries
;;;;------------------------------

(defn general-query
  [db path]
  (reaction (get-in @db path)))

(register-sub :tabs general-query)


;;;;----------------------------
;;;; Handlers
;;;;----------------------------

(register-handler
  :initialize
  (fn [_ [_ tabs]]
    (.log js/console tabs)
    (console/log "Initialized" tabs)
    {:tabs tabs}))

(register-handler
  :tab-created
  (fn [app-state [_ msg]]
    (console/log "Created" (:tab msg))
    (assoc app-state :tabs (conj (:tabs app-state) (:tab msg)))))

(defn remove-tab
  "Removed a tab from a collection by id"
  [col id]
  (remove #(= (:id %) id) col))

(register-handler
  :log-content
  (fn [app-state [_ content]]
    (console/log "Log event:" content)
    app-state
    ))

(register-handler
  :tab-updated
  (fn [app-state [_ msg]]
    (console/log "Updated:" msg)
    (assoc app-state
      :tabs
      (-> (:tabs app-state)
          (remove-tab (:tabId msg))
          (conj (:tab msg))))
    ))


(register-handler
  :tab-removed
  (fn [app-state [_ msg]]
    (console/log "Removed:" (:tabId msg) msg)
    (assoc app-state :tabs (remove-tab (:tabs app-state) (:tabId msg)))))

(register-handler
  :tab-replaced
  (fn [app-state [_ msg]]
    (console/log "Replaced:" msg)
    app-state))

;;;;----------------------------
;;;; Components
;;;;----------------------------


(defn list-tabs [tabs]
  (for [tab (sort-by :index tabs)]
    ^{:key (:id tab)}
    [:tr
     [:td (:id tab)]
     [:td (:title tab)]
     [:td (:url tab)]]))

(defn tab-list []
  (let [tabs (subscribe [:tabs])]
    (fn []
      [:div
       [:table {:class "table table-striped table-hover"}
        [:thead
         [:tr
          [:th "#"]
          [:th "Title"]
          [:th "URL"]]]
        [:tbody
         (list-tabs @tabs)]
        ]
       [:button {:on-click #(storage/set {:links (map (fn [m] (select-keys m [:index :url])) @tabs)})} "Save me"]
       [:button {:on-click #(go (console/log (<! (storage/get nil))))} "Get"]
       [:button {:on-click #(go (console/log "Usage: " (<! (storage/bytes-in-use nil))))} "Usage"]
       [:button {:on-click #(storage/clear)} "Clear"]
       ])))


;;;;----------------------------
;;;; Chrome subscriptions
;;;;----------------------------

(defn dispatch-on-channel
  "Dispatched msg when there's content received on the channel returned by
  function chan-f."
  [msg chan-f]
  (go (while true
        (let [content (<! (chan-f))]
          (dispatch [msg content])))))


(defn mount-components []
  (reagent/render-component [tab-list] (.getElementById js/document "tab-list")))


(defn init []
  (console/log "Initialized tabler.core")
  (go (let [c (<! (windows/get-current))]
        (dispatch [:initialize (:tabs c)])))
  (let [bg (runtime/connect)]
    (dispatch-on-channel :log-content storage/on-changed)
    (dispatch-on-channel :tab-created tabs/tab-created-events)
    (dispatch-on-channel :tab-removed tabs/tab-removed-events)
    (dispatch-on-channel :tab-updated tabs/tab-updated-events)
    (dispatch-on-channel :tab-replaced tabs/tab-replaced-events)
    (go (>! bg :lol-i-am-a-popup)
        (console/log "Background said: " (<! bg))))
  (mount-components))
