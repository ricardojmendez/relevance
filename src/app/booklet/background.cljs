(ns booklet.background
  (:require [cljs.core.async :refer [>! <!]]
            [booklet.utils :refer [dispatch-on-channel]]
            [khroma.log :as console]
            [khroma.runtime :as runtime]
            [khroma.windows :as windows]
            [khroma.storage :as storage]
            [khroma.idle :as idle]
            [khroma.tabs :as tabs]
            [reagent.core :as reagent]
            [re-frame.core :refer [dispatch register-sub register-handler subscribe dispatch-sync]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))




;;;;-------------------------------------
;;;; Functions
;;;;-------------------------------------

;; Let's track two values:
;; - How long a tab has been open
;; - How long was it active
;; Need to track active tabs, and from there credit the URL.


(defn now [] (.now js/Date))

(def relevant-tab-keys [:windowId :id :active :url :selected :start-time])

(def select-tab-keys #(select-keys % relevant-tab-keys))
(def add-tab-times #(assoc % :start-time (if (:active %) (now) 0)))

(def tab-data-path [:app-state :tab-tracking])
(def url-time-path [:data :urls])

(defn process-tab
  "Filters a tab down to the relevant keys, and adds a start time which is
  now if the tab is active, or 0 otherwise.

  Keeping the time on the tab itself, since we may end up with multiple tabs
  open to the same URL. Might make sense to track it all in one, using always
  the last one... but for now I'm assuming that if you have it active in two
  tabs, it's doubly important (you found it twice and forgot about it)."
  [tab]
  (->
    tab
    select-tab-keys
    add-tab-times))


(defn process-tabs
  "Take the tabs we have, filter them down and return them grouped by id."
  [tabs]
  (->>
    tabs
    (map process-tab)
    (reduce #(assoc % (:id %2) %2) {})))


;;;;-------------------------------------
;;;; Handlers
;;;;-------------------------------------


(register-handler
  :handle-activation
  (fn [app-state [_ tab]]
    (console/log "Handling activation" tab)
    (if tab
      (assoc-in app-state
                (conj tab-data-path (:id tab))
                (-> tab
                    select-tab-keys
                    (assoc :active true
                           :start-time (now))))
      app-state)))


(register-handler
  :handle-deactivation
  (fn [app-state [_ tab removed?]]
    (console/log "Deactivating" tab removed?)
    (when (or (:active tab)
              (< 0 (:start-time tab)))
      (dispatch [:track-time (:url tab) (- (now) (:start-time tab))]))
    (if removed?
      app-state
      (assoc-in app-state
                (conj tab-data-path (:id tab))
                (assoc tab :active false
                           :start-time 0)))))


(register-handler
  :idle-state-change
  (fn [app-state [_ message]]
    (let [state       (:newState message)
          all-tabs    (get-in app-state tab-data-path)
          active-tabs (if (= "active" state)
                        (get-in app-state [:app-state :idle])
                        (filter :active (vals all-tabs)))
          message     (if (= "active" state) :handle-activation :handle-deactivation)]
      (console/log "State changed to" state message)
      (doseq [tab active-tabs]
        (dispatch [message tab]))
      ;; We only store the idle tabs on the app state if we actually idled any.
      ;; That way we avoid losing the originally stored idled tabs when we
      ;; first go from active->idle and then from idle->locked (the first one
      ;; would find tabs, the second one would and would overwrite the original
      ;; saved set with an empty list).
      (if active-tabs
        (assoc-in app-state [:app-state :idle] active-tabs)
        app-state)



      )))

(register-handler
  ::tab-activated
  (fn [app-state [_ {:keys [activeInfo]}]]
    (let [{:keys [tabId windowId]} activeInfo
          all-tabs    (get-in app-state tab-data-path)
          prev-active (filter #(and (:active %)
                                    (not= tabId (:id %))
                                    (= windowId (:windowId %)))
                              (vals all-tabs))
          ]
      ;; Highly unlikely we'll have more than one active tab per window,
      ;; but let's handle it in case we missed an event
      (doseq [tab prev-active]
        (dispatch [:handle-deactivation tab]))
      (dispatch [:handle-activation (get all-tabs tabId)])
      (console/log "Activated" tabId "from window" windowId)
      (console/log "Previously active" prev-active))
    app-state))

(register-handler
  ::tab-created
  (fn [app-state [_ {:keys [tab]}]]
    (console/log "Created" tab)
    (when (:active tab)
      ;; If we just created an active tab, make sure we go through the activation cycle
      (dispatch [::tab-activated {:activeInfo {:tabId    (:id tab)
                                               :windowId (:windowId tab)}}]))
    (assoc-in app-state
              (conj tab-data-path (:id tab))
              (process-tab tab))
    ))


(register-handler
  ::tab-removed
  (fn [app-state [_ msg]]
    (let [id   (:tabId msg)
          tabs (get-in app-state tab-data-path)
          tab  (get tabs id)]
      (console/trace "Removed id:" id "Previous" tab (:active tab))
      (dispatch [:handle-deactivation tab true])                      ; We're not only deactivating it, we're destroying it
      (assoc-in app-state tab-data-path (dissoc tabs id)))))


(register-handler
  ::tab-replaced
  (fn [app-state [_ {:keys [added removed]}]]
    ;; When we get a tab-replaced, we only get two ids. We don't get any
    ;; other tab information. We'll treat this as a remove and a create,
    ;; and let those event handlers handle it.
    (console/log "Replaced" added removed)
    (dispatch [::tab-removed {:tabId removed}])
    (go (dispatch [::tab-created {:tab (<! (tabs/get-tab added))}]))
    app-state
    ))

(register-handler
  ::tab-updated
  (fn [app-state [_ {:keys [tabId tab]}]]
    (let [old-tab (get-in app-state (conj tab-data-path tabId))]
      (when (and (:active tab)
                 (not= (:url old-tab)
                       (:url tab)))
        (do
          (console/log "Tab URL changed while active")
          (dispatch [:handle-deactivation old-tab])
          (dispatch [:handle-activation tab]))
        )
      ;; TODO: Handle case where the url changed
      )
    (console/log "Updated" tabId tab (get-in app-state (conj tab-data-path tabId)))
    app-state
    ))

(register-handler
  :track-time
  (fn [app-state [_ url time]]
    (console/log time "milliseconds spent at" url)
    app-state))

;; TODO
;; - Track URL state changes
;; - Log time spent on URL


;; TODO: Handle detached tabs, looks like we don't get an activate for them.


(register-handler
  :start-tracking
  (fn [app-state [_ tabs]]
    (-> app-state
        (assoc-in tab-data-path (process-tabs tabs))
        (assoc-in url-time-path
                  (or (get-in app-state url-time-path) {})))))


(defn start-tracking []
  (go (dispatch [:start-tracking (<! (tabs/query))])))


;;;;-------------------------------------
;;;; Initialization
;;;;-------------------------------------


(defn init-time-tracking []
  (go (let [window (<! (windows/get-current))
            state  (<! (idle/query-state 30))]
        (dispatch-sync [:initialize (:tabs window)])
        (dispatch-sync [:idle-state-change {:newState state}])
        (start-tracking)))
  (dispatch-on-channel :log-content storage/on-changed)
  (dispatch-on-channel ::tab-activated tabs/on-activated)
  (dispatch-on-channel ::tab-created tabs/on-created)
  (dispatch-on-channel ::tab-removed tabs/on-removed)
  (dispatch-on-channel ::tab-updated tabs/on-updated)
  (dispatch-on-channel ::tab-replaced tabs/on-replaced)
  (idle/set-detection-interval 60)
  (dispatch-on-channel :idle-state-change idle/on-state-changed))

(defn init []
  (init-time-tracking)
  (go-loop
    [conns (runtime/on-connect)]
    (let [content (<! conns)]
      (console/log "On background. Got message: " (<! content))
      (>! content "Hello from background"))
    (recur conns)))
