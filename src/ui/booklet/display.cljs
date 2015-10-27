(ns booklet.display
  (:require [booklet.utils :refer [on-channel from-transit]]
            [cljs.core.async :refer [>! <!]]
            [cljs.core :refer [random-uuid]]
            [cljsjs.react-bootstrap]
            [khroma.idle :as idle]
            [khroma.log :as console]
            [khroma.storage :as storage]
            [reagent.core :as reagent]
            [re-frame.core :refer [dispatch register-sub register-handler subscribe dispatch-sync]])
  (:require-macros [cljs.core :refer [goog-define]]
                   [cljs.core.async.macros :refer [go go-loop]]
                   [reagent.ratom :refer [reaction]]))


;;;;------------------------------
;;;; Queries
;;;;------------------------------

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
  ::initialize
  (fn [_]
    ;; Fake a ::storage-changed message to load the data from storage
    (go (dispatch [::storage-changed {:changes {:data {:newValue (:data (<! (storage/get)))}}}]))
    {:app-state {}
     :ui-state  {:section :time-track}}))


(register-handler
  :modal-info-set
  (fn [app-state [_ info]]
    (assoc-in app-state [:ui-state :modal-info] info)))


(register-handler
  ::storage-changed
  (fn [app-state [_ message]]
    (let [new-value (get-in message [:changes :data :newValue])
          data      (from-transit new-value)]
      (if (not-empty data)
        (assoc app-state :data data)
        app-state
        ))
    ))


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
    (when (= section current) [:span {:class "sr-only"} "(current)"])]])

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
          [navbar-item "Times" :time-track @section]
          ]
         [:form {:class "navbar-form navbar-left", :role "search"}
          [:div {:class "form-group"}
           [:input {:type "text", :class "form-control", :placeholder "Search"}]]
          [:button {:type "submit", :class "btn btn-default"} "Submit"]]
         [:ul {:class "nav navbar-nav navbar-right"}
          [navbar-item "Export" :export @section]
          [navbar-item "Import" :import @section]]]]])))


;; We could actually move modal-confirm to a component namespace, parametrize it.
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

(defn list-tabs [tabs disp-key]
  (->>
    tabs
    (sort-by :index)
    (map-indexed
      (fn [i tab]
        (let [url     (:url tab)
              favicon (:favIconUrl tab)]
          ^{:key i}
          [:tr
           [:td {:class "col-sm-1"} (when disp-key (disp-key tab))]
           [:td {:class "col-sm-6"} [:a
                                     {:href url :target "_blank"}
                                     (if favicon
                                       [:img {:src    favicon
                                              :width  16
                                              :height 16}])
                                     (:title tab)]]
           [:td {:class "col-sm-5"} url]])))))


(defn div-timetrack []
  (let [url-times  (subscribe [:data :url-times])
        url-values (reaction (filter-tabs (vals @url-times)))
        to-list    (reaction (sort-by #(* -1 (:time %)) @url-values))]
    (fn []
      [:div
       [:div {:class "page-header"} [:h2 "Times"]]
       [:table {:class "table table-striped table-hover"}
        [:thead
         [:tr
          [:th "#"]
          [:th "Title"]
          [:th "URL"]]]
        [:tbody
         (list-tabs @to-list :time)]
        ]])
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


(def component-dir {:export     data-export
                    :import     data-import
                    :time-track div-timetrack})


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


(defn mount-components []
  (reagent/render-component [navbar] (.getElementById js/document "navbar"))
  (reagent/render-component [main-section] (.getElementById js/document "main-section")))


(defn ^:export main []
  (dispatch-sync [::initialize])
  (on-channel storage/on-changed dispatch ::storage-changed)
  (idle/set-detection-interval 60)
  (mount-components))

(main)