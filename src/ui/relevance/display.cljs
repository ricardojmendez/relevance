(ns relevance.display
  (:require [relevance.utils :refer [on-channel from-transit time-display host-key hostname]]
            [cljs.core.async :refer [>! <!]]
            [cljs.core :refer [random-uuid]]
            [cljsjs.react-bootstrap]
            [khroma.idle :as idle]
            [khroma.log :as console]
            [khroma.runtime :as runtime]
            [khroma.storage :as storage]
            [reagent.core :as reagent]
            [re-frame.core :refer [dispatch register-sub register-handler subscribe dispatch-sync]]
            [relevance.io :as io])
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
(register-sub :raw-data general-query)
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
  :data-import
  (fn [app-state [_ transit-data]]
    ;; We actually just need to save it, since ::storage-changed takes care
    ;; of loading it and importing it.
    (io/save-raw transit-data #(runtime/send-message :reload-data))
    (-> app-state
        (assoc-in [:ui-state :section] :url-times)
        (assoc-in [:app-state :import] nil))
    ))


(register-handler
  ::initialize
  (fn [_]
    ;; Fake a ::storage-changed message to load the data from storage
    (go (dispatch [::storage-changed {:changes {:data {:newValue (:data (<! (storage/get)))}}}]))
    {:app-state {}
     :ui-state  {:section :intro}}))


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
        (assoc app-state :data data :raw-data new-value)
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
         [:a {:class "navbar-brand" :href "http://numergent.com" :target "_blank"} "Relevance"]]
        [:div {:class "collapse navbar-collapse", :id "bs-example-navbar-collapse-1"}
         [:ul {:class "nav navbar-nav"}
          [navbar-item "Introduction" :intro @section]
          [navbar-item "Times per page" :url-times @section]
          [navbar-item "Times per site" :site-times @section]
          ]
         #_[:form {:class "navbar-form navbar-left", :role "search"}
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

(defn list-urls [urls site-data]
  (->>
    urls
    (map-indexed
      (fn [i tab]
        (let [url     (:url tab)
              favicon (:favIconUrl (get site-data (host-key (hostname url))))
              title   (:title tab)
              display (if (< 100 (count title))
                        (apply str (concat(take 100 title) "..."))
                        title
                        )]
          ^{:key i}
          [:tr
           [:td {:class "col-sm-2"} (time-display (:time tab))]
           [:td {:class "col-sm-9 col-sm-offset-1"} [:a
                                     {:href url :target "_blank"}
                                     (if favicon
                                       [:img {:src    favicon
                                              :width  16
                                              :height 16}])
                                     display]]
           ])))))


(defn div-urltimes []
  (let [url-times  (subscribe [:data :url-times])
        site-times (subscribe [:data :site-times])
        url-values (reaction (filter-tabs (vals @url-times)))
        to-list    (reaction (sort-by #(* -1 (:time %)) @url-values))]
    (fn []
      [:div
       [:div {:class "page-header"} [:h2 "Time reading at a page"]]
       [:table {:class "table table-striped table-hover"}
        [:thead
         [:tr
          [:th "#"]
          [:th "Title"]]]
        [:tbody
         (list-urls @to-list @site-times)]
        ]])
    ))

(defn div-intro []
  [:div
   [:div {:class "page-header col-sm-10 col-sm-offset-1"}
    [:h1 "Welcome!"]]
   [:div {:class "col-sm-10 col-sm-offset-1"}
    [:h2 "Thanks for installing Relevance"]
    [:p "Relevance is a smart tab organizer. It’s nonintrusive and fully private. When you activate it your tabs are sorted based on the duration you are actively viewing it combined with the total time you actively browse pages on that domain. It will allow you to discover greater insights about your browsing habits."]
    [:p "Relevance will keep track of the pages you actually read, and how long you spend reading them. This information is kept completely private, on your local browser. As you open tabs, its knowledge of what's important to you grows, and when you activate it the tabs  for your current window are ordered depending on how long you have spent reading them."]
    [:p "This creates a natural arrangement where the tabs you have spent the longest on, which are expected to be the most relevant, are placed first, and the ones you haven't read at all are shunted to the end."]
    [:p "I'm a tab-aholic. I normally do a search, start opening the tabs that seem interesting, and then as I flip through them, I end up opening even more links on tabs as they seem relevant."]
    [:p "Next thing I know I have a huge mess of tabs, and it's hard to remember which one I've read, or which one is more relevant."]
    [:p "I wrote Relevance to help manage that. I've found it very useful in organizing what I should be focusing on, and I hope it'l be useful for you as well."]
    ]
   [:div {:class "col-sm-10 col-sm-offset-1"}
    [:h2 "Preview software"]
    [:p "Relevance is a software preview, and I'll be happy to hear your comments. If you have any suggestions on what you think might make Relevance better, "
     [:a {:href "https://twitter.com/intent/tweet?text=Hey%20@argesric%20about%20&hashtags=relevance" :target "_blank"}
      "please reach out on Twitter"]
     " or "
     [:a {:href "http://numergent.com/#contact"} "through our site"]
     "."]]
   [:div {:class "col-sm-10 col-sm-offset-1"}
    [:h2 "Experimental StartPage integration"]
    [:p "Ever ran into a situation where you re-do a search, but can't remember which ones were the most important links?"]
    [:p "This version of Relevance has an experimental integration with "
     [:a {:href "https://startpage.com" :target "_blank"} "StartPage"]
     ". After you run a search, it'll look at the results on your current page and re-prioritize the links shown to bring to the front those you have spent the longest reading."]
    [:p "Every search engine behaves differently, so if there's enough interest, I could extend this integration to others as well."]]
   [:div
    [:div {:class "col-sm-6"}
     [:div {:class "col-sm-12"}
      [:strong "Before"]]
     [:div {:class "col-sm-12"}
      [:img {:src "http://numergent.com/images/relevance/relevance-0.3-clojure chrome before.png"}]]
     ]
    [:div {:class "col-sm-6"}
     [:div {:class "col-sm-12"}
      [:strong "After"]]
     [:div {:class "col-sm-12"}
      [:img {:src "http://numergent.com/images/relevance/relevance-0.3-clojure chrome after.png"}]]
     ]]
   ]
  )

(defn div-sitetimes []
  (let [site-times (subscribe [:data :site-times])
        sites      (reaction (vals @site-times))
        to-list    (reaction (sort-by #(* -1 (:time %)) @sites))]
    (fn []
      [:div
       [:div {:class "page-header"} [:h2 "Time spent at a site"]]
       [:table {:class "table table-striped table-hover"}
        [:thead
         [:tr
          [:th "#"]
          [:th "Site"]]]
        [:tbody
         (->>
           @to-list
           (map-indexed
             (fn [i site]
               (let [url     (:host site)
                     favicon (:favIconUrl site)]
                 ^{:key i}
                 [:tr
                  [:td {:class "col-sm-1"} (time-display (:time site))]
                  [:td {:class "col-sm-6"} (if favicon
                                             [:img {:src    favicon
                                                    :width  16
                                                    :height 16}])
                   url]
                  ]))))]
        ]])
    ))


(defn data-export []
  (let [data (subscribe [:raw-data])]
    (fn []
      [:div
       [:div {:class "page-header"} [:h2 "Current data"]]
       [:p (str "Copy the text below to a safe location. Size: " (count @data))]
       [:textarea {:class     "form-control"
                   :rows      30
                   :read-only true
                   :value     @data}
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
        [:p "Your entire data will be replaced with the information below."]]
       [:textarea {:class     "form-control"
                   :rows      30
                   :value     @import-data
                   :on-change #(dispatch [:app-state-item path (-> % .-target .-value)])}]
       [:a {:class    "btn btn-danger btn-sm"
            :on-click #(dispatch [:data-import @import-data])} "Import"]
       ])))


(def component-dir {:export     data-export
                    :import     data-import
                    :intro      div-intro
                    :url-times  div-urltimes
                    :site-times div-sitetimes})


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