(ns booklet.background
  (:require [clojure.set :refer [difference]]
            [cljs.core.async :refer [>! <!]]
            [booklet.utils :refer [on-channel from-transit to-transit]]
            [cognitect.transit :as transit]
            [khroma.log :as console]
            [khroma.runtime :as runtime]
            [khroma.windows :as windows]
            [khroma.storage :as storage]
            [khroma.idle :as idle]
            [khroma.tabs :as tabs]
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

(def relevant-tab-keys [:windowId :id :active :url :selected :start-time :title :favIconUrl])

(def select-tab-keys #(select-keys % relevant-tab-keys))
(def add-tab-times #(assoc % :start-time (if (:active %) (now) 0)))

(def tab-data-path [:app-state :tab-tracking])
(def url-time-path [:data :url-times])

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


(defn tab-list-to-map
  [tabs]
  (reduce #(assoc % (:id %2) %2) {} tabs))

(defn process-tabs
  "Take the tabs we have, filter them down and return them grouped by id."
  [tabs]
  (->>
    tabs
    (map process-tab)
    tab-list-to-map))


;;;;-------------------------------------
;;;; Handlers
;;;;-------------------------------------


;; :data-import currently gets dispatched from both booklet.core
;; and booklet.background, not entirely happy with that. Needs
;; further clean up
(register-handler
  :data-import
  (fn [app-state [_ transit-data]]
    (let [new-data (from-transit transit-data)]
      (console/log "New data on import" new-data)
      ;; Create a new id if we don't have one
      (when (empty? (:instance-id new-data))
        (dispatch [:data-set :instance-id (.-uuid (random-uuid))]))
      ;; Dispatch instead of just doing an assoc so that it's also saved
      (doseq [[key item] new-data]
        (dispatch [:data-set key item]))
      ;; Once we've dispatched these, let's dispatch evaluate the state
      (dispatch [:data-import-done])
      (-> app-state
          (assoc-in [:ui-state :section] :time-track)
          (assoc-in [:app-state :import] nil))
      )))


(register-handler
  :data-import-done
  (fn [app-state [_]]
    (let [suspend-info    (get-in app-state [:data :suspend-info])
          old-tabs        (:active-tabs suspend-info)
          all-tabs        (get-in app-state tab-data-path)
          active-tabs     (filter :active (vals all-tabs))
          ;; Find all inactive tabs
          now-inactive    (filter #(empty? (filter
                                             (fn [t]
                                               (and (= (:id t) (:id %))
                                                    (= (:url t) (:url %))))
                                             active-tabs))
                                  old-tabs)
          ;; Get the still active tabs
          still-active    (difference (set old-tabs) (set now-inactive))
          active-old-tabs (tab-list-to-map still-active)]
      ;; De-activate every inactive tab
      (doseq [tab now-inactive]
        (dispatch [:handle-deactivation tab true (:time suspend-info)]))
      (console/log "From suspend:" suspend-info)
      (console/log "Still active:" active-old-tabs)
      app-state
      )
    ))


(register-handler
  :data-set
  (fn [app-state [_ key item]]
    (let [new-state    (assoc-in app-state [:data key] item)
          transit-data (to-transit (:data new-state))]
      (console/log "Saving" key)
      (storage/set {:data transit-data})
      (console/log "New state" new-state)
      new-state)
    ))

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
  (fn
    ; We get three parameters: the tab, if the item is being removed,
    ; and the time at which it was deactivated (which defaults to now)
    [app-state [_ tab removed? end-time]]
    (console/log " Deactivating " tab removed?)
    (when (or (:active tab)
              (< 0 (:start-time tab)))
      (dispatch [:track-time tab (- (or end-time (now))
                                    (:start-time tab))]))
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
          active-tabs (if (= " active " state)
                        (get-in app-state [:app-state :idle])
                        (filter :active (vals all-tabs)))
          message     (if (= " active " state) :handle-activation :handle-deactivation)]
      (console/log " State changed to " state message)
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
  :start-tracking
  (fn [app-state [_ tabs]]
    ; (dispatch [:data-set :url-times {}])
    (-> app-state
        (assoc-in tab-data-path (process-tabs tabs))
        (assoc-in url-time-path
                  (or (get-in app-state url-time-path) {})))))

(register-handler
  :suspend
  (fn [app-state [_]]
    (let [all-tabs    (get-in app-state tab-data-path)
          active-tabs (filter :active (vals all-tabs))]
      (dispatch [:data-set :suspend-info {:time        (now)
                                          :active-tabs active-tabs}]))
    ;; The message itself is not relevant, we only care that we are being suspended
    app-state
    ))


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
      ; (console/log " Activated " tabId " from window " windowId)
      ; (console/log " Previously active " prev-active)
      )
    app-state))

(register-handler
  ::tab-created
  (fn [app-state [_ {:keys [tab]}]]
    ; (console/log " Created " tab)
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
      ; (console/trace " Removed id: " id " Previous " tab (:active tab))
      (dispatch [:handle-deactivation tab true])                      ; We're not only deactivating it, we're destroying it
      (assoc-in app-state tab-data-path (dissoc tabs id)))))

(register-handler
  ::tab-replaced
  (fn [app-state [_ {:keys [added removed]}]]
    ;; When we get a tab-replaced, we only get two ids. We don't get any
    ;; other tab information. We'll treat this as a remove and a create,
    ;; and let those event handlers handle it.
    ; (console/log " Replaced " added removed)
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
        (console/log " Tab URL changed while active ")
        (dispatch [:handle-deactivation old-tab])
        (dispatch [:handle-activation tab])
        ))
    ; (console/log " Updated " tabId tab (get-in app-state (conj tab-data-path tabId)))
    app-state
    ))


(register-handler
  :track-time
  (fn [app-state [_ tab time]]
    (let [url-times (get-in app-state url-time-path)
          url-key   (:url tab)
          url-time  (or (get url-times url-key)
                        {:url       (:url tab)
                         :time      0
                         :timestamp 0})
          ;; Don't track two messages too close
          track?    (and (not-empty url-key)
                         (< 100 (- (now) (:timestamp url-time))))
          new-time  (assoc url-time :time (+ (:time url-time) time)
                                    :title (:title tab)
                                    :favIconUrl (:favIconUrl tab)
                                    :timestamp (now))]
      (console/log time track? " milliseconds spent at " url-key)
      (console/log " Previous " url-time)
      (when track?
        (dispatch [:data-set :url-times (assoc url-times url-key new-time)]))
      app-state
      )))

;; TODO
;; - Track URL state changes
;; - Log time spent on URL


;; TODO: Handle detached tabs, looks like we don't get an activate for them.



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
  (on-channel runtime/on-suspend dispatch-sync :suspend)
  (on-channel runtime/on-suspend-canceled dispatch-sync :log-content)
  ; (on-channel storage/on-changed dispatch :log-content)
  (on-channel tabs/on-activated dispatch ::tab-activated)
  (on-channel tabs/on-created dispatch ::tab-created)
  (on-channel tabs/on-removed dispatch ::tab-removed)
  (on-channel tabs/on-updated dispatch ::tab-updated)
  (on-channel tabs/on-replaced dispatch ::tab-replaced)
  (idle/set-detection-interval 60)
  (on-channel idle/on-state-changed dispatch :idle-state-change))

(defn init []
  (init-time-tracking))
