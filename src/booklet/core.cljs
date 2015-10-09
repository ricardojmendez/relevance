(ns booklet.core
  (:require [ajax.core :refer [GET POST PUT]]
            [cljs.core.async :refer [>! <!]]
            [cljs.core :refer [random-uuid]]
            [cljsjs.react-bootstrap]
            [khroma.idle :as idle]
            [khroma.log :as console]
            [khroma.runtime :as runtime]
            [khroma.storage :as storage]
            [khroma.tabs :as tabs]
            [khroma.windows :as windows]
            [reagent.core :as reagent]
            [re-frame.core :refer [dispatch register-sub register-handler subscribe dispatch-sync]])
  (:require-macros [cljs.core :refer [goog-define]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))


;;;;------------------------------
;;;; Queries
;;;;------------------------------

(goog-define api-uri "http://localhost:3000")

(defn general-query
  [db path]
  (reaction (get-in @db path)))

;; Application data, will be saved
(register-sub :data general-query)
;; Transient data items
(register-sub :ui-state general-query)
(register-sub :app-state general-query)




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
   :id   (.-uuid (random-uuid))
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

;; Tab items we actually care about
(def relevant-tab-items [:index :url :title :favIconUrl])


;;;;----------------------------
;;;; Handlers
;;;;----------------------------

(register-handler
  :app-state-item
  (fn [app-state [_ path item]]
    (assoc-in app-state path item)))


(register-handler
  :data-import
  (fn [app-state [_ json-data]]
    (let [new-data (clojure.walk/keywordize-keys (js->clj (.parse js/JSON json-data)))]
      (doseq [[key item] new-data]
        ;; Dispatch instead of just doing an assoc so that it's also saved
        (dispatch [:data-set key item]))
      (-> app-state
          (assoc-in [:ui-state :section] :groups)
          (assoc-in [:app-state :import] nil))
      )))

(register-handler
  :data-set
  (fn [app-state [_ key item]]
    (storage/set {key item})
    (assoc-in app-state [:data key] item)
    ))


(register-handler
  :initialize
  (fn [_ [_ tabs]]
    (go (dispatch [:storage-loaded (<! (storage/get))]))
    {:app-state {:tabs tabs}
     :ui-state  {:section :monitor}}))

(register-handler
  :group-add
  (fn [app-state [_]]
    (let [tabs    (get-in app-state [:app-state :tabs])
          to-save (map (fn [m] (select-keys m relevant-tab-items)) tabs)
          groups  (conj (or (get-in app-state [:data :groups]) '())
                        (group-from-tabs to-save))]
      (dispatch [:data-set :groups groups])
      app-state)))

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
      (dispatch [:data-set :groups groups])
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
    (dispatch [:data-set :groups (->> (get-in app-state [:data :groups])
                                      (remove #(= % old-group))
                                      (cons new-group))])
    app-state))


(register-handler
  :idle-state-change
  (fn [app-state [_ message]]
    (let [path    [:app-state :interval]
          handler (get-in app-state path)
          state   (:newState message)]
      (console/log "Idle state change:" state handler)
      (cond
        (and (nil? handler)
             (= state "active")) (assoc-in app-state path (js/setInterval #(dispatch [:snapshot-take]) 60000))
        (and (some? handler)
             (not= state "active")) (do
                                      (js/clearInterval handler)
                                      (assoc-in app-state path nil))
        :else app-state
        ))))


(register-handler
  :log-content
  (fn [app-state [_ content]]
    (console/log "Log event:" content)
    app-state
    ))


(register-handler
  :modal-info-set
  (fn [app-state [_ info]]
    (assoc-in app-state [:ui-state :modal-info] info)))


(register-handler
  :snapshot-take
  (fn [app-state [_]]
    (let [path      [:data :snapshots]
          snapshots (or (get-in app-state path) '())
          tabs      (->> (get-in app-state [:app-state :tabs])
                         filter-tabs
                         (map #(select-keys % relevant-tab-items)))
          new-group (group-from-tabs tabs)
          last-snap (or (get-in app-state [:app-state :last-snapshot])
                        (first (sort-by #(* -1 (:date %)) snapshots)))
          is-last?  (= (set (map :url tabs))
                       (set (map :url (:tabs last-snap))))
          save?     (and (< 0 (count tabs))
                         (not is-last?))
          ]
      (when save? (dispatch [:data-set :snapshots (conj snapshots new-group)]))
      ; (console/log "Current" (set tabs) "Last" (set (:tabs last-snap)) "Last snap" last-snap)
      ; (console/log "Tick tock snapshot " save? (.now js/Date))
      (if save?
        (assoc-in app-state [:app-state :last-snapshot] new-group)
        app-state)
      )
    ))

(register-handler
  :snapshot-post
  (fn [app-state [_]]
    (let [to-send (select-keys (:data app-state) [:instance-id :snapshots])]
      (GET (str api-uri "/api/echo/" "hello")
           {:handler       #(console/log "GET Handler" %)
            :error-handler #(console/log "GET Error" %)})
      (POST (str api-uri "/api/snapshot/many")
            {:params        to-send
             :handler       #(dispatch [:snapshot-post-success (:snapshots to-send)])
             :error-handler #(dispatch [:log-content %])})
      )
    app-state
    )
  )

(register-handler
  :snapshot-post-success
  (fn [app-state [_ snapshots]]
    (console/log "Saved" snapshots)
    ;; TODO: Remove only the snapshots we saved
    (dispatch [:data-set :snapshots nil])
    app-state
    ))


(register-handler
  :storage-loaded
  (fn [app-state [_ data]]
    ;; We create a new id if fir any reason we don't have one
    (when (empty? (:instance-id data))
      (dispatch [:data-set :instance-id (.-uuid (random-uuid))]))
    (dispatch [:snapshot-post])
    ;; Return new data state. We don't return the new id because data-set also
    ;; saves it, the call below only sets the internal state
    (->> data
         (merge (:data app-state))
         (assoc app-state :data))
    ))


(register-handler
  :tab-created
  (fn [app-state [_ msg]]
    (console/trace "Created" (:tab msg))
    (assoc app-state [:app-state :tabs] (conj (get-in app-state [:app-state :tabs]) (:tab msg)))))


(register-handler
  :tab-updated
  (fn [app-state [_ msg]]
    (console/trace "Updated:" msg)
    (assoc-in app-state
              [:app-state :tabs]
              (-> (get-in app-state [:app-state :tabs])
                  (remove-tab (:tabId msg))
                  (conj (:tab msg))))
    ))

(register-handler
  :tab-removed
  (fn [app-state [_ msg]]
    (console/trace "Removed:" (:tabId msg) msg)
    (assoc-in app-state [:app-state :tabs] (remove-tab (get-in app-state [:app-state :tabs]) (:tabId msg)))))

(register-handler
  :tab-replaced
  (fn [app-state [_ msg]]
    (console/trace "Replaced:" msg)
    ; We don't need to create a new item for the tab being added, as
    ; we'll also get an "update" message which will add it.
    (assoc-in app-state [:app-state :tabs] (remove-tab (get-in app-state [:app-state :tabs]) (:removed msg)))))



;;;;------------------------------
;;;; Utils
;;;;------------------------------

(def Modal (reagent/adapt-react-class js/ReactBootstrap.Modal))
(def ModalBody (reagent/adapt-react-class js/ReactBootstrap.ModalBody))
(def ModalFooter (reagent/adapt-react-class js/ReactBootstrap.ModalFooter))
(def ModalHeader (reagent/adapt-react-class js/ReactBootstrap.ModalHeader))



;;;;----------------------------
;;;; Components
;;;;----------------------------


(defn navbar-item [label section current]
  [:li {:class (when (= section current) "active")}
   [:a {:on-click #(dispatch [:app-state-item [:ui-state :section] section])} label
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
         [:a {:class "navbar-brand" :href "http://numergent.com" :target "_new"} "Booklet"]]
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
  (let [tabs (reaction (filter-tabs @(subscribe [:app-state :tabs])))]
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
       [:a {:class    "btn btn-primary btn-sm"
            :on-click #(dispatch [:group-add])} "Save me"]
       [:a {:class    "btn btn-primary btn-sm"
            :on-click #(go (console/log (<! (storage/get))))} "Get"]
       [:a {:class    "btn btn-primary btn-sm"
            :on-click #(go (console/log "Usage: " (<! (storage/bytes-in-use))))} "Usage"]
       ; [:button {:on-click #(storage/clear)} "Clear"]
       ])))

(defn list-groups
  [group-list editable? group-edit edit-label]
  (for [group group-list]
    ^{:key (:date group)}
    [:div
     [:div
      (if (= group group-edit)
        [initial-focus-wrapper
         [:input {:type      "text"
                  :class     "form-control"
                  :value     edit-label
                  :on-change #(dispatch-sync [:group-label-set (-> % .-target .-value)])
                  :on-blur   #(do
                               (dispatch [:group-update group (assoc group :name edit-label)])
                               (dispatch [:group-edit-set nil]))
                  }]]
        [:h3 {:on-click #(dispatch [:group-edit-set group])} (group-label group)]
        )
      (if editable? [:small [:a {:on-click #(dispatch [:group-delete-set group])} "Delete"]])
      ]

     [:table {:class "table table-striped table-hover"}
      [:thead
       [:tr
        [:th "#"]
        [:th "Title"]
        [:th "URL"]]]
      [:tbody
       (list-tabs (filter-tabs (:tabs group)) true)]
      ]])
  )

(defn div-tab-groups []
  (let [tab-groups (subscribe [:data :groups])
        group-edit (subscribe [:ui-state :group-edit])
        label      (subscribe [:ui-state :group-label])
        to-list    (reaction (sort-by #(* -1 (:date %)) @tab-groups))]
    (fn []
      [:div
       [modal-confirm]
       [:div {:class "page-header"} [:h2 "Previous groups"]]
       (list-groups @to-list true @group-edit @label)
       ])
    ))

(defn div-snapshots []
  (let [snapshots (subscribe [:data :snapshots])
        to-list   (reaction (sort-by #(* -1 (:date %)) @snapshots))]
    (fn []
      [:div
       [:div {:class "page-header"} [:h2 "Snapshots"]]
       (list-groups @to-list false nil nil)
       ])
    ))


(defn data-export []
  (let [data      (subscribe [:data])
        as-string (reaction (.stringify js/JSON (clj->js @data) nil 2))]
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

(defn data-import []
  (let [path        [:app-state :import]
        import-data (subscribe path)]
    (fn []
      [:div
       [:div {:class "page-header"} [:h2 "Import data"]]
       [:div {:class "alert alert-warning"}
        [:h4 "Warning!"]
        [:p "Any data item for which there is a key on the JSON below be replaced!"]]
       [:textarea {:class     "form-control"
                   :rows      30
                   :value     @import-data
                   :on-change #(dispatch [:app-state-item path (-> % .-target .-value)])}]
       [:a {:class    "btn btn-danger btn-sm"
            :on-click #(dispatch [:data-import @import-data])} "Import"]
       ])))


(def component-dir {:monitor   current-tabs
                    :groups    div-tab-groups
                    :export    data-export
                    :import    data-import
                    :snapshots div-snapshots})


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
  (console/log api-uri)
  (go (let [window (<! (windows/get-current))
            state  (<! (idle/query-state 30))]
        (dispatch-sync [:initialize (:tabs window)])
        (dispatch-sync [:idle-state-change {:newState state}])))
  (let [bg (runtime/connect)]
    (dispatch-on-channel :log-content storage/on-changed)
    (dispatch-on-channel :tab-created tabs/on-created)
    (dispatch-on-channel :tab-removed tabs/on-removed)
    (dispatch-on-channel :tab-updated tabs/on-updated)
    (dispatch-on-channel :tab-replaced tabs/on-replaced)
    (idle/set-detection-interval 60)
    (dispatch-on-channel :idle-state-change idle/on-state-changed)
    (go (>! bg :lol-i-am-a-popup)
        (console/log "Background said: " (<! bg))))
  (mount-components))
