(ns relevance.display
  (:require [relevance.utils :refer [on-channel from-transit time-display host-key hostname
                                     ms-hour ms-day ms-week]]
            [cljs.core.async :refer [>! <!]]
            [cljs.core :refer [random-uuid]]
            [cljsjs.react-bootstrap]
            [clojure.string :as string]
            [khroma.idle :as idle]
            [khroma.log :as console]
            [khroma.runtime :as runtime]
            [khroma.storage :as storage]
            [reagent.core :as reagent]
            [re-frame.core :refer [dispatch register-sub register-handler subscribe dispatch-sync]]
            [relevance.io :as io]
            [relevance.utils :as utils]
            [relevance.settings :refer [default-settings]]
            )
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
(register-sub :settings general-query)
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

;;;;----------------------------
;;;; Handlers
;;;;----------------------------

(register-handler
  :app-state-item
  (fn [app-state [_ path item]]
    (js/ga "send" "screenview" #js {:screenName (name item)})
    (assoc-in app-state path item)))


(register-handler
  :data-import
  (fn [app-state [_ transit-data]]
    ;; We actually just need to save it, since ::storage-changed takes care
    ;; of loading it and importing it.
    (io/save-raw :data transit-data #(runtime/send-message :reload-data))
    (-> app-state
        (assoc-in [:ui-state :section] :url-times)
        (assoc-in [:app-state :import] nil))
    ))


(register-handler
  ::initialize
  (fn [_]
    (go
      (dispatch [:settings-set (or (<! (io/load :settings))
                                   default-settings)])
      ;; Fake a ::storage-changed message to load the data from storage
      (dispatch [::storage-changed {:changes {:data {:newValue (:data (<! (storage/get)))}}}]))
    {:app-state {}
     :settings  default-settings
     :ui-state  {:section :intro}}))


(register-handler
  :modal-info-set
  (fn [app-state [_ info]]
    (assoc-in app-state [:ui-state :modal-info] info)))

(register-handler
  :settings-parse
  (fn [app-state [_ settings]]
    (let [ignore-set (utils/to-string-set (:ignore-set settings))]
      (dispatch [:settings-set {:ignore-set ignore-set} true])
      app-state)
    ))

(register-handler
  :settings-set
  (fn [app-state [_ settings save?]]
    ; (console/log "Saving" settings save?)
    (when save?
      ;; We tell the backend to reload the data after saving the settings, since
      ;; they can have an effect on behavior.
      (io/save :settings settings #(runtime/send-message :reload-data)))
    (assoc app-state :settings settings)))

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


(defn nav-left-item [label class section current]
  [:li {:class (when (= section current) "active")}
   [:a {:on-click #(dispatch [:app-state-item [:ui-state :section] section])}
    [:i {:class class}]
    [:p label]
    ]]
  )

(defn nav-left []
  (let [section (subscribe [:ui-state :section])]
    (fn []
      [:ul {:class "nav"}
       (nav-left-item "Introduction" "pe-7s-home" :intro @section)
       (nav-left-item "Page times" "pe-7s-note2" :url-times @section)
       (nav-left-item "Site times" "pe-7s-note2" :site-times @section)
       (nav-left-item "Settings" "pe-7s-config" :settings @section)
       (nav-left-item "Export data" "pe-7s-box1" :export @section)
       (nav-left-item "Import data" "pe-7s-attention" :import @section)]))
  )

(defn nav-top []
  (let [section (subscribe [:ui-state :section])]
    (fn []
      [:div {:class "navbar-header"}
       [:a {:class "navbar-brand"}
        (condp = @section
          :intro "About Relevance"
          :url-times "Time reading a page"
          :site-times "Time visiting a site"
          :export "Export your Relevance data"
          :settings "Settings"
          :import "Import a Relevance backup"
          "")
        ]])))

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
              favicon (:icon (get site-data (host-key (hostname url))))
              title   (:title tab)
              label   (if (empty? title)
                        url
                        title)
              display (if (< 100 (count label))
                        (apply str (concat (take 100 label) "..."))
                        label)
              age-ms  (- (.now js/Date) (:ts tab))
              ;; Colors picked at http://www.w3schools.com/tags/ref_colorpicker.asp
              color   (cond
                        (< age-ms ms-hour) "#00ff00"
                        (< age-ms ms-day) "#00cc00"
                        (< age-ms (* 3 ms-day)) "#009900"
                        (< age-ms (* 7 ms-day)) "#ff8000"
                        (< age-ms (* 14 ms-day)) "#cc6600"
                        :else "#994c00"
                        )
              ]
          ^{:key i}
          [:tr
           [:td {:class "col-sm-2"}
            (time-display (:time tab))]
           [:td {:class "col-sm-8"}
            [:a
             {:href url :target "_blank"}
             (if favicon
               [:img {:src    favicon
                      :width  16
                      :height 16}])
             display]]
           [:td {:class "col-sm-2"}
            [:i (merge {:class "fa fa-circle" :style {:color color}})]
            (time-display (quot age-ms 1000))]
           ])))))


(defn div-urltimes []
  (let [url-times  (subscribe [:data :url-times])
        site-times (subscribe [:data :site-times])
        url-values (reaction (filter-tabs (vals @url-times)))
        to-list    (reaction (sort-by #(* -1 (:time %)) @url-values))]
    (fn []
      [:div {:class "row"}
       [:div {:class "card"}
        [:div {:class "content table-responsive table-full-width"}
         [:table {:class "table table-striped table-hover"}
          [:thead
           [:tr
            [:th "Time"]
            [:th "Title"]
            [:th "Last visit"]]]
          [:tbody
           (list-urls @to-list @site-times)
           ]]]]
       ])
    ))

(defn div-sitetimes []
  (let [site-times (subscribe [:data :site-times])
        sites      (reaction (vals @site-times))
        to-list    (reaction (sort-by #(* -1 (:time %)) @sites))]
    (fn []
      [:div {:class "row"}
       [:div {:class "card"}
        [:div {:class "content table-responsive table-full-width"}
         [:table {:class "table table-striped table-hover"}
          [:thead
           [:tr
            [:th "Time"]
            [:th "Site"]]]
          [:tbody
           (->>
             @to-list
             (map-indexed
               (fn [i site]
                 (let [url  (:host site)
                       icon (:icon site)]
                   ^{:key i}
                   [:tr
                    [:td {:class "col-sm-1"} (time-display (:time site))]
                    [:td {:class "col-sm-6"} (if icon
                                               [:img {:src    icon
                                                      :width  16
                                                      :height 16}])
                     url]
                    ]))))]
          ]]]])
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


(defn data-export []
  (let [data (subscribe [:raw-data])]
    (fn []
      [:div {:class "col-sm-10 col-sm-offset-1"}
       [:div {:class "page-header"} [:h2 "Current data"]]
       [:p (str "Copy the text below to a safe location. Size: " (count @data))]
       [:textarea {:class     "form-control"
                   :rows      30
                   :read-only true
                   :value     @data}
        ]
       ])))


(defn data-import []
  (let [import-data (reagent/atom "")]
    (fn []
      [:div {:class "col-sm-10 col-sm-offset-1"}
       [:div {:class "page-header"} [:h2 "Import data"]]
       [:div {:class "alert alert-warning"}
        [:h4 "Warning!"]
        [:p "Your entire data will be replaced with the information below."]]
       [:textarea {:class     "form-control"
                   :rows      30
                   :value     @import-data
                   :on-change #(reset! import-data (-> % .-target .-value))}]
       [:a {:class    "btn btn-primary btn-sm"
            :on-click #(dispatch [:data-import @import-data])} "Import"]
       ])))

(defn div-settings []
  (let [ignore-set (subscribe [:settings :ignore-set])
        our-ignore (reagent/atom (string/join "\n" (sort @ignore-set)))
        ]
    (fn []
      [:div {:class "col-sm-12"}
       [:div {:class "row"}
        [:div {:class "col-sm-6"}
         [:h3 "Ignore domains"]
         [:p "Type on the left domains that you want ignore, one per line."]
         [:p {:class "alert alert-info"} [:strong "Heads up! "] "Adding a domain to the ignore list will remove the data Relevance currently has for it."]
         ]
        [:div {:class "col-sm-6"}
         [:textarea {:class     "form-control"
                     :value     @our-ignore
                     :rows      10
                     :on-change #(reset! our-ignore (-> % .-target .-value))}
          ]]
        ]
       [:div {:class "row"}
        [:a {:class    "btn btn-danger btn-sm"
             :on-click #(dispatch [:settings-parse {:ignore-set @our-ignore}])} "Save settings"]]
       ])))


(def component-dir {:export     data-export
                    :import     data-import
                    :intro      div-intro
                    :settings   div-settings
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
  (reagent/render-component [nav-left] (.getElementById js/document "nav-left"))
  (reagent/render-component [nav-top] (.getElementById js/document "nav-top"))
  (reagent/render-component [main-section] (.getElementById js/document "main-section")))


(defn ^:export main []
  (dispatch-sync [::initialize])
  (on-channel storage/on-changed dispatch ::storage-changed)
  (idle/set-detection-interval 60)
  (mount-components))

(main)