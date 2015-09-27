(ns booklet.core
  (:require [cljs.core.async :refer [>! <!]]
            [cljsjs.react-bootstrap]
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
(register-sub :ui-state general-query)


;;;;----------------------------
;;;; Functions
;;;;----------------------------

(defn dispatch-on-press-enter [e d]
  (if (= 13 (.-which e))
    (dispatch d)))



(defn filter-tabs
  "Filters out the tabs we will not show or manipulate, for instance, chrome extensions"
  [tabs]
  (remove #(.startsWith (:url %) "chrome") tabs))

(defn group-from-tabs
  "Takes a tabset and returns a new group containing them and some extra data"
  [tabs]
  {:date (.now js/Date)
   :tabs tabs})

(defn group-label [group]
  (or (:name group) (:date group)))

(def initial-focus-wrapper
  (with-meta identity
             {:component-did-mount #(.focus (reagent/dom-node %))}))

(defn remove-tab
  "Removed a tab from a collection by id"
  [col id]
  (remove #(= (:id %) id) col))



;;;;----------------------------
;;;; Handlers
;;;;----------------------------

(register-handler
  :initialize
  (fn [_ [_ tabs]]
    (go (dispatch [:storage-loaded (<! (storage/get))]))
    {:data     {:tabs tabs}
     :ui-state {:section :monitor}}))

(register-handler
  :group-delete-set
  (fn [app-state [_ group]]
    (dispatch [:modal-info-set {:header       "Please confirm"
                                :body         (str "Do you want to delete tab group " (group-label group) "?")
                                :action-label "Kill it"
                                :action       #(do
                                                (dispatch [:modal-info-set nil])
                                                (dispatch [:group-delete group]))}])
    app-state))

(register-handler
  :group-delete
  (fn [app-state [_ group]]
    (let [original (get-in app-state [:data :groups])
          groups   (remove #(= group %) original)]
      (dispatch [:groups-set groups])
      app-state)))


(register-handler
  :group-edit-set
  (fn [app-state [_ group]]
    (dispatch [:group-label-set (group-label group)])
    (assoc-in app-state [:ui-state :group-edit] group)))


(register-handler
  :group-label-set
  (fn [app-state [_ label]]
    (assoc-in app-state [:ui-state :group-label] label)))

(register-handler
  :group-update
  (fn [app-state [_ old-group new-group]]
    (dispatch [:groups-set (->> (get-in app-state [:data :groups])
                                (remove #(= % old-group))
                                (cons new-group))])
    app-state))


(register-handler
  :groups-set
  (fn [app-state [_ groups]]
    (storage/set {:groups groups})
    (assoc-in app-state [:data :groups] groups)
    ))

(register-handler
  :modal-info-set
  (fn [app-state [_ info]]
    (assoc-in app-state [:ui-state :modal-info] info)))

(register-handler
  :storage-loaded
  (fn [app-state [_ data]]
    (assoc-in app-state [:data :groups] (:groups data))
    ))

(register-handler
  :tab-created
  (fn [app-state [_ msg]]
    (console/trace "Created" (:tab msg))
    (assoc app-state [:data :tabs] (conj (get-in app-state [:data :tabs]) (:tab msg)))))


(register-handler
  :log-content
  (fn [app-state [_ content]]
    (console/log "Log event:" content)
    app-state
    ))


(register-handler
  :set-app-state-item
  (fn [app-state [_ path item]]
    (assoc-in app-state path item)))

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
      (dispatch [:groups-set groups])
      app-state)))

;;;;------------------------------
;;;; Utils
;;;;------------------------------

(def Modal (reagent/adapt-react-class js/ReactBootstrap.Modal))
(def ModalBody (reagent/adapt-react-class js/ReactBootstrap.ModalBody))
(def ModalFooter (reagent/adapt-react-class js/ReactBootstrap.ModalFooter))
(def ModalHeader (reagent/adapt-react-class js/ReactBootstrap.ModalHeader))
(def ModalTitle (reagent/adapt-react-class js/ReactBootstrap.ModalTitle))
(def ModalTrigger (reagent/adapt-react-class js/ReactBootstrap.ModalTrigger))



;;;;----------------------------
;;;; Components
;;;;----------------------------


(defn navbar-item [label section current]
  [:li {:class (when (= section current) "active")}
   [:a {:on-click #(dispatch [:set-app-state-item [:ui-state :section] section])} label
    (when (= section current) [:span {:class "sr-only"} "(current)"])]]
  )

(defn navbar []
  (let [section (subscribe [:ui-state :section])]
    (fn []
      [:nav {:class "navbar navbar-default"}
       [:div {:class "container-fluid"}
        [:div {:class "navbar-header"}
         [:button {:type "button", :class "navbar-toggle collapsed", :data-toggle "collapse", :data-target "#bs-example-navbar-collapse-1"}
          [:span {:class "sr-only"} "Toggle navigation"]
          [:span {:class "icon-bar"}]
          [:span {:class "icon-bar"}]
          [:span {:class "icon-bar"}]]
         [:a {:class "navbar-brand", :href "#"} "Brand"]]
        [:div {:class "collapse navbar-collapse", :id "bs-example-navbar-collapse-1"}
         [:ul {:class "nav navbar-nav"}
          [navbar-item "Monitor" :monitor @section]
          [navbar-item "Groups" :groups @section]
          [navbar-item "Snapshots" :snapshots @section]]
         [:form {:class "navbar-form navbar-left", :role "search"}
          [:div {:class "form-group"}
           [:input {:type "text", :class "form-control", :placeholder "Search"}]]
          [:button {:type "submit", :class "btn btn-default"} "Submit"]]
         [:ul {:class "nav navbar-nav navbar-right"}
          [navbar-item "Export" :export @section]
          [navbar-item "Import" :import @section]]]]])))


(defn modal-confirm []
  (let [modal-info (subscribe [:ui-state :modal-info])
        ;; On the next one, we can't use not-empty because (= nil (not-empty nil)), and :show expects true/false,
        ;; not a truth-ish value.
        show?      (reaction (not (empty? @modal-info)))]
    (fn []
      [Modal {:show @show? :onHide #(dispatch [:modal-info-set nil])}
       [ModalHeader
        [:h4 (:header @modal-info)]]
       [ModalBody
        [:div
         (:body @modal-info)]]
       [ModalFooter
        [:button {:type     "reset"
                  :class    "btn btn-default"
                  :on-click #(dispatch [:modal-info-set nil])} "Cancel"]
        [:button {:type     "submit"
                  :class    "btn btn-primary"
                  :on-click (:action @modal-info)} (:action-label @modal-info)]
        ]])))

(defn list-tabs [tabs is-history?]
  (for [tab (sort-by :index tabs)]
    (let [url     (:url tab)
          favicon (:favIconUrl tab)
          action  (if is-history?
                    {:href url :target "_blank"}
                    {:on-click #(tabs/activate (:id tab))}
                    )]
      ^{:key (:id tab)}
      [:tr
       [:td {:class "col-sm-1"} (if-not is-history? (:id tab))]
       [:td {:class "col-sm-6"} [:a
                                 action
                                 (if favicon
                                   [:img {:src    favicon
                                          :width  16
                                          :height 16}])
                                 (:title tab)]]
       [:td {:class "col-sm-5"} url]])))

(defn current-tabs []
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
         (list-tabs @tabs false)]
        ]
       [:button {:on-click #(dispatch [:tabset-save])} "Save me"]
       [:button {:on-click #(go (console/log (<! (storage/get))))} "Get"]
       [:button {:on-click #(go (console/log "Usage: " (<! (storage/bytes-in-use))))} "Usage"]
       ; [:button {:on-click #(storage/clear)} "Clear"]
       ])))

(defn tab-groups []
  (let [tab-groups (subscribe [:data :groups])
        group-edit (subscribe [:ui-state :group-edit])
        label      (subscribe [:ui-state :group-label])
        to-list    (reaction (sort-by #(* -1 (:date %)) @tab-groups))]
    (fn []
      [:div
       [modal-confirm]
       [:div {:class "page-header"} [:h2 "Previous groups"]]
       (doall
         (for [group @to-list]
           ^{:key (:date group)}
           [:div
            [:div
             (if (= group @group-edit)
               [initial-focus-wrapper
                [:input {:type      "text"
                         :class     "form-control"
                         :value     @label
                         :on-change #(dispatch-sync [:group-label-set (-> % .-target .-value)])
                         :on-blur   #(do
                                      (dispatch [:group-update group (assoc group :name @label)])
                                      (dispatch [:group-edit-set nil]))
                         }]]
               [:h3 {:on-click #(dispatch [:group-edit-set group])} (group-label group)]
               )
             [:small [:a {:on-click #(dispatch [:group-delete-set group])} "Delete"]]
             ]

            [:table {:class "table table-striped table-hover"}
             [:thead
              [:tr
               [:th "#"]
               [:th "Title"]
               [:th "URL"]]]
             [:tbody
              (list-tabs (filter-tabs (:tabs group)) true)]
             ]]))
       ]
      )
    ))

(defn data-export []
  (let [data      (subscribe [:data])
        as-string (reaction (.stringify js/JSON (clj->js (dissoc @data :tabs)) nil 2))]
    (fn []
      [:div
       [:div {:class "page-header"} [:h2 "Current data"]]
       [:p (str "Copy the JSON below to a safe location. Size: " (count @as-string))]
       [:textarea {:class     "form-control"
                   :rows      30
                   :read-only true
                   ;; We will not export the current tab list
                   :value     @as-string}
        ]
       ])))


(def component-dir {:monitor current-tabs
                    :groups  tab-groups
                    :export  data-export})


(defn main-section []
  (let [section   (subscribe [:ui-state :section])
        component (reaction (get component-dir @section))]
    (fn []
      (if (some? @component)
        [@component]
        [:div "Work in progress..."]))))

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
  (reagent/render-component [navbar] (.getElementById js/document "navbar"))
  (reagent/render-component [main-section] (.getElementById js/document "main-section")))


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
