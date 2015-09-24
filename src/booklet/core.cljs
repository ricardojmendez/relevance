(ns booklet.core
  (:require [cljs.core.async :refer [>! <!]]
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

(register-sub :data general-query)
(register-sub :state general-query)


;;;;----------------------------
;;;; Functions
;;;;----------------------------

(defn filter-tabs
  "Filters out the tabs we will not show or manipulate, for instance, chrome extensions"
  [tabs]
  (remove #(.startsWith (:url %) "chrome") tabs))


(defn group-from-tabs
  "Takes a tabset and returns a new group containing them and some extra data"
  [tabs]
  {:date (.now js/Date)
   :tabs tabs})


;;;;----------------------------
;;;; Handlers
;;;;----------------------------

(register-handler
  :initialize
  (fn [_ [_ tabs]]
    (.log js/console tabs)
    (console/trace "Initializing with:" tabs)
    (go (dispatch [:storage-loaded (<! (storage/get))]))
    {:data {:tabs tabs}}))

(register-handler
  :storage-loaded
  (fn [app-state [_ data]]
    (console/trace "Storage loaded:" data)
    (assoc-in app-state [:data :groups] (:groups data))
    ))

(register-handler
  :tab-created
  (fn [app-state [_ msg]]
    (console/trace "Created" (:tab msg))
    (assoc app-state [:data :tabs] (conj (get-in app-state [:data :tabs]) (:tab msg)))))

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
    (console/trace "Updated:" msg)
    (assoc-in app-state
      [:data :tabs]
      (-> (get-in app-state [:data :tabs])
          (remove-tab (:tabId msg))
          (conj (:tab msg))))
    ))


(register-handler
  :tab-removed
  (fn [app-state [_ msg]]
    (console/trace "Removed:" (:tabId msg) msg)
    (assoc-in app-state [:data :tabs] (remove-tab (get-in app-state [:data :tabs]) (:tabId msg)))))

(register-handler
  :tab-replaced
  (fn [app-state [_ msg]]
    (console/trace "Replaced:" msg)
    ; We don't need to create a new item for the tab being added, as
    ; we'll also get an "update" message which will add it.
    (assoc-in app-state [:data :tabs] (remove-tab (get-in app-state [:data :tabs]) (:removed msg)))))


(register-handler
  :tabset-save
  (fn [app-state [_]]
    (let [tabs    (get-in app-state [:data :tabs])
          to-save (map (fn [m] (select-keys m [:index :url :id :title])) tabs)
          groups  (conj (or (get-in app-state [:data :groups]) '())
                        (group-from-tabs to-save))]
      (storage/set {:groups groups})
      (assoc-in app-state [:data :groups] groups))
    ))


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
  (let [tabs (reaction (filter-tabs @(subscribe [:data :tabs])))]
    (fn []
      [:div
       [:div {:class "page-header"} [:h2 "Current tabs"]]
       [:table {:class "table table-striped table-hover"}
        [:thead
         [:tr
          [:th "#"]
          [:th "Title"]
          [:th "URL"]]]
        [:tbody
         (list-tabs @tabs)]
        ]
       [:button {:on-click #(dispatch [:tabset-save])} "Save me"]
       [:button {:on-click #(go (console/log (<! (storage/get))))} "Get"]
       [:button {:on-click #(go (console/log "Usage: " (<! (storage/bytes-in-use))))} "Usage"]
       [:button {:on-click #(storage/clear)} "Clear"]
       ])))

(defn list-groups [groups]
  (console/log "list-groups" groups)
  (for [group groups]
    ^{:key (:date group)}
    [:div
     [:div
      [:h3 (:date group)]
      [:small [:a {:on-click #(console/log "Deleting" group)} "Delete"]]
      ]

     [:table {:class "table table-striped table-hover"}
      [:thead
       [:tr
        [:th "#"]
        [:th "Title"]
        [:th "URL"]]]
      [:tbody
       (list-tabs (filter-tabs (:tabs group)))]
      ]]))

(defn tab-groups []
  (let [tab-groups (subscribe [:data :groups])]
    (fn []
      [:div
       [:div {:class "page-header"} [:h2 "Previous groups"]]
       (list-groups (sort-by #(* -1 (:date %)) @tab-groups))
       ]
      )
    ))


;;;;----------------------------
;;;; Chrome subscriptions
;;;;----------------------------

(defn dispatch-on-channel
  "Dispatched msg when there's content received on the channel returned by
  function chan-f."
  [msg chan-f]
  (let [channel (chan-f)]
    (go (while true
          (let [content (<! channel)]
            (dispatch [msg content]))))
    ))


(defn mount-components []
  (reagent/render-component [tab-list] (.getElementById js/document "tab-list"))
  (reagent/render-component [tab-groups] (.getElementById js/document "tab-groups")))


(defn init []
  (console/log "Initialized booklet.core")
  (go (let [c (<! (windows/get-current))]
        (dispatch [:initialize (:tabs c)])))
  (let [bg (runtime/connect)]
    (dispatch-on-channel :log-content storage/on-changed)
    (dispatch-on-channel :tab-created tabs/on-created)
    (dispatch-on-channel :tab-removed tabs/on-removed)
    (dispatch-on-channel :tab-updated tabs/on-updated)
    (dispatch-on-channel :tab-replaced tabs/on-replaced)
    (go (>! bg :lol-i-am-a-popup)
        (console/log "Background said: " (<! bg))))
  (mount-components))
