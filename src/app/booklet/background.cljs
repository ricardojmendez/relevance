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


(defn now [] (.now js/Date))

(def relevant-tab-keys [:windowId :id :active :url :selected :start-time :title :favIconUrl])

(def select-tab-keys #(select-keys % relevant-tab-keys))
(def add-tab-times #(assoc % :start-time (if (:active %) (now) 0)))

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
      (console/trace "New data on import" new-data)
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
          (assoc-in [:app-state :import] nil)
          (assoc :data new-data))
      )))


(register-handler
  :data-import-done
  (fn [app-state [_]]
    (let [suspend-info (get-in app-state [:data :suspend-info])
          old-tab      (:active-tab suspend-info)
          current-tab  (:active-tab app-state)
          is-same?     (and (= (:id old-tab) (:id current-tab))
                            (= (:url old-tab) (:url current-tab))
                            (= (:windowId old-tab) (:windowId current-tab)))]
      ;; De-activate every inactive tab
      (if is-same?
        (dispatch [:handle-deactivation old-tab (:time suspend-info)])
        (dispatch [:handle-activation old-tab (:start-time old-tab)]))
      (console/trace "From suspend:" is-same? suspend-info)
      (dispatch [:data-set :suspend-info] nil)
      app-state
      )
    ))


(register-handler
  :data-set
  (fn [app-state [_ key item]]
    (let [new-state    (assoc-in app-state [:data key] item)
          transit-data (to-transit (:data new-state))]
      (storage/set {:data transit-data})
      new-state)
    ))

(register-handler
  :handle-activation
  (fn [app-state [_ tab start-time]]
    (console/trace "Handling activation" tab)
    (if tab
      (assoc app-state
        :active-tab
        (-> tab
            select-tab-keys
            (assoc :active true
                   :start-time (or start-time (now)))))
      app-state)))


(register-handler
  :handle-deactivation
  (fn
    ; We get two parameters: the tab, and optionally the time at which it
    ; was deactivated (which defaults to now)
    [app-state [_ tab end-time]]
    (console/trace " Deactivating " tab)
    (when (or (:active tab)
              (< 0 (:start-time tab)))
      (dispatch [:track-time tab (- (or end-time (now))
                                    (:start-time tab))]))
    app-state))


(register-handler
  :idle-state-change
  (fn [app-state [_ message]]
    (let [state      (:newState message)
          action     (if (= "active" state) :handle-activation :handle-deactivation)
          active-tab (if (= :handle-activation action)
                       (get-in app-state [:app-state :idle])
                       (:active-tab app-state))
          ]
      (console/trace " State changed to " state action)
      ;; We only store the idle tabs on the app state if we actually idled any.
      ;; That way we avoid losing the originally stored idled tabs when we
      ;; first go from active->idle and then from idle->locked (the first one
      ;; would find tabs, the second one would and would overwrite the original
      ;; saved set with an empty list).
      (if active-tab
        (do
          (dispatch [action active-tab])
          (-> app-state
              (assoc-in [:app-state :idle] active-tab)
              (assoc :active-tab nil)))
        app-state)
      )))


(register-handler
  :suspend
  ;; The message itself is not relevant, we only care that we are being suspended
  (fn [app-state [_]]
    (dispatch [:data-set :suspend-info {:time       (now)
                                        :active-tab (:active-tab app-state)}])
    app-state))


(register-handler
  ::tab-activated
  (fn [app-state [_ {:keys [activeInfo]}]]
    (let [{:keys [tabId windowId]} activeInfo
          active-tab (:active-tab app-state)
          replace?   (= windowId (:windowId active-tab))]
      ; (console/trace ::tab-activated tabId windowId replace? active-tab)
      (if replace?
        (do
          (dispatch [:handle-deactivation active-tab])
          (go (dispatch [:handle-activation (<! (tabs/get tabId))]))
          (assoc app-state :active-tab nil))                          ; :handle-activation is responsible for setting it
        app-state
        ))))


(register-handler
  ::tab-updated
  (fn [app-state [_ {:keys [tabId tab]}]]
    ; (console/trace ::tab-updated (:title tab) tabId tab (:active-tab app-state))
    (let [active-tab (:active-tab app-state)
          are-same?  (= tabId (:id active-tab))]
      (when (and are-same?
                 (:active tab)
                 (not= (:url active-tab)
                       (:url tab)))
        ; (console/trace " Tab URL changed while active " tab active-tab)
        (dispatch [:handle-deactivation active-tab])
        (dispatch [:handle-activation tab]))
      ;; We can receive multiple tab-updated messages one after the other, before
      ;; the dispatches above have had a change to handle activation/deactivation.
      ;; Therefore, I change the title and URL right away, in case this gets
      ;; triggered again and we compare it again (to avoid a double trigger of
      ;; the URL change condition above).
      (if are-same?
        (assoc app-state :active-tab (merge active-tab (select-keys tab [:title :url])))
        app-state)
      )))


(register-handler
  :track-time
  (fn [app-state [_ tab time]]
    (let [url-times (or (get-in app-state url-time-path) {})
          url       (or (:url tab) "")
          url-key   (.hashCode url)
          url-time  (or (get url-times url-key)
                        {:url       (:url tab)
                         :time      0
                         :timestamp 0})
          ;; Don't track two messages too close together
          track?    (and (not= 0 url-key)
                         (< 100 (- (now) (:timestamp url-time))))
          new-time  (assoc url-time :time (+ (:time url-time) time)
                                    :title (:title tab)
                                    :favIconUrl (:favIconUrl tab)
                                    :timestamp (now))]
      (console/trace time track? " milliseconds spent at " url-key tab)
      ; (console/trace " Previous " url-time)
      (when track?
        (dispatch [:data-set :url-times (assoc url-times url-key new-time)]))
      app-state
      )))

(register-handler
  ::window-focus
  (fn [app-state [_ {:keys [windowId]}]]
    (console/trace "Current window" windowId)
    (let [active-tab (:active-tab app-state)
          replacing? (not= windowId (:windowId active-tab))]
      (if replacing?
        (do
          (dispatch [:handle-deactivation active-tab])
          ;; TODO: Add alarm when none
          (when (not= windowId windows/none)
            (go (dispatch [:handle-activation
                           (first (<! (tabs/query {:active true :windowId windowId})))])))
          (assoc app-state :active-tab nil))
        app-state)
      )
    ))


;;;;-------------------------------------
;;;; Initialization
;;;;-------------------------------------


(defn init-time-tracking []
  (go (let [state  (<! (idle/query-state 30))
            window (<! (windows/get-last-focused {:populate false}))]
        (dispatch-sync [:initialize])
        (dispatch-sync [::window-focus {:windowId (:id window)}])
        (dispatch-sync [:idle-state-change {:newState state}])
        ))
  (on-channel runtime/on-suspend dispatch-sync :suspend)
  (on-channel runtime/on-suspend-canceled dispatch-sync :log-content)
  (on-channel tabs/on-activated dispatch ::tab-activated)
  (on-channel tabs/on-updated dispatch ::tab-updated)
  (on-channel windows/on-focus-changed dispatch ::window-focus)
  (idle/set-detection-interval 60)
  (on-channel idle/on-state-changed dispatch :idle-state-change))

(defn init []
  (init-time-tracking))
