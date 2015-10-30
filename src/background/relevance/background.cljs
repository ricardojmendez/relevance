(ns relevance.background
  (:require [cljs.core.async :refer [>! <!]]
            [relevance.data :as data]
            [relevance.io :as io]
            [relevance.migrations :as migrations]
            [relevance.utils :refer [on-channel url-key host-key hostname is-http?]]
            [khroma.alarms :as alarms]
            [khroma.context-menus :as menus]
            [khroma.idle :as idle]
            [khroma.log :as console]
            [khroma.tabs :as tabs]
            [khroma.runtime :as runtime]
            [khroma.windows :as windows]
            [re-frame.core :refer [dispatch register-sub register-handler subscribe dispatch-sync]]
            [khroma.extension :as ext]
            [khroma.browser-action :as browser]
            )
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))



;;;;-------------------------------------
;;;; Settings
;;;;-------------------------------------


(def window-alarm "window-alarm")
(def non-http-penalty 0.05)
(def relevant-tab-keys [:windowId :id :active :url :start-time :title :favIconUrl])
(def select-tab-keys #(select-keys % relevant-tab-keys))

(defn now [] (.now js/Date))


;;;;-------------------------------------
;;;; Functions
;;;;-------------------------------------


(defn check-window-status
  "Checks if we have any focused window, and compares it against the
  window id for the active tab. If they do not match, it dispatches
  a ::window-focus message"
  [tab]
  (go
    (let [last-focused (<! (windows/get-last-focused {:populate false}))]
      (when (and (:focused last-focused)
                 (not= (:id last-focused) (:windowId tab)))
        (dispatch [::window-focus {:windowId (:id last-focused)}])))))


(defn hook-to-channels
  "Hooks up to the various events we'll need to set up to.
  We won't call it until after the initial import is done, so that we
  don't end up receiving events when we don't yet have the environment
  set up to handle them."
  []
  ;; We should use dispatch for anything that does not absolutely require
  ;; immediate handling, to avoid interferring with the regular initialization
  ;; and event flow.  On this case, I' using it only for log-content,
  ;; which has no effect on app-state, and for on-suspend, which we want to
  ;; handle immediately.
  (on-channel alarms/on-alarm dispatch ::on-alarm)
  (on-channel browser/on-clicked dispatch ::on-clicked-button)
  (on-channel runtime/on-message dispatch ::on-message)
  (on-channel runtime/on-suspend dispatch-sync :suspend)
  (on-channel runtime/on-suspend-canceled dispatch-sync :log-content)
  (on-channel tabs/on-activated dispatch ::tab-activated)
  (on-channel tabs/on-updated dispatch ::tab-updated)
  (on-channel windows/on-focus-changed dispatch ::window-focus)
  (on-channel menus/on-clicked dispatch ::on-clicked-menu)
  (idle/set-detection-interval 60)
  (on-channel idle/on-state-changed dispatch :idle-state-change))

(defn open-results-tab []
  (go (let [ext-url (str (ext/get-url "/") "index.html")
            ;; We could just get the window-id from the tab, but that still
            ;; requires us to make an extra call for the other tabs
            window  (<! (windows/get-current))
            our-tab (first (filter #(= ext-url (:url %)) (:tabs window)))]
        (if our-tab
          (tabs/activate (:id our-tab))
          (tabs/create {:url ext-url})))))



(defn time-score [tab url-times site-times]
  (let [url       (:url tab)
        idx       (:index tab)
        tab-time  (:time (get url-times (url-key url)))
        site-time (:time (get site-times (host-key (hostname url))))
        total     (+ tab-time site-time)
        score     (if (is-http? url) total (* total non-http-penalty))
        ]
    (or (when tab-time score)
        (- 2000 idx))))

(defn sort-tabs! [window-id data]
  (go
    (let [{:keys [url-times site-times]} data
          tabs (->> (:tabs (<! (windows/get window-id)))
                    (map #(assoc % :time (time-score % url-times site-times)))
                    (sort-by #(* -1 (:time %)))
                    (map-indexed #(hash-map :index %1
                                            :id (:id %2))))]
      (doseq [tab tabs]
        (tabs/move (:id tab) {:index (:index tab)}))
      )))


;;;;-------------------------------------
;;;; Handlers
;;;;-------------------------------------


(register-handler
  ::initialize
  (fn [_]
    (go
      (dispatch [:data-load (<! (io/load))])
      (dispatch [::window-focus {:windowId (:id (<! (windows/get-last-focused {:populate false})))}])
      ;; We should only hook to the channels once, so we do it during the :initialize handler
      (hook-to-channels))
    {:app-state {}}))


(register-handler
  :data-load
  (fn [app-state [_ loaded]]
    (let [new-data (migrations/migrate-to-latest loaded)]
      (console/trace "Data load" loaded "migrated" new-data)
      ;; Save the migrated data we just received
      (io/save new-data)
      ;; Process the suspend info
      (let [suspend-info (:suspend-info new-data)
            old-tab      (:active-tab suspend-info)
            current-tab  (:active-tab app-state)
            is-same?     (and (= (:id old-tab) (:id current-tab))
                              (= (:url old-tab) (:url current-tab))
                              (= (:windowId old-tab) (:windowId current-tab)))]
        (if is-same?
          (dispatch [:handle-activation old-tab (:start-time old-tab)])
          (dispatch [:handle-deactivation old-tab (:time suspend-info)])))
      (-> app-state
          (assoc :data (dissoc new-data :suspend-info))))))


(register-handler
  :data-set
  (fn [app-state [_ key item]]
    (let [new-state (assoc-in app-state [:data key] item)]
      (io/save (:data new-state))
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
    ;; We get two parameters: the tab, and optionally the time at which it
    ;; was deactivated (which defaults to now)
    [app-state [_ tab end-time]]
    (console/trace " Deactivating " tab)
    (when (< 0 (:start-time tab))
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
  ::on-alarm
  (fn [app-state [_ {:keys [alarm]}]]
    (when (= window-alarm (:name alarm))
      (check-window-status (:active-tab app-state)))
    app-state
    ))

(register-handler
  ::on-clicked-button
  (fn [app-state [_ {:keys [tab]}]]
    ;; Force it to track the time up until now
    (let [active-tab (:active-tab app-state)]
      (dispatch [:handle-deactivation active-tab])
      (dispatch [:handle-activation active-tab]))
    (dispatch [:on-relevance-sort-tabs tab])
    app-state
    ))

(register-handler
  ::on-clicked-menu
  (fn [app-state [_ {:keys [info tab]}]]
    (dispatch [(keyword (:menuItemId info)) tab])
    app-state
    ))

(register-handler
  ::on-message
  (fn [app-state [_ {:keys [message sender]}]]
    ; (console/log "GOT INTERNAL MESSAGE" message "from" sender)
    (condp = (keyword message)
      :reload-data (go (dispatch [:data-load (<! (io/load))])))
    app-state
    ))


(register-handler
  :on-relevance-show-data
  (fn [app-state [_]]
    (open-results-tab)
    app-state))


(register-handler
  :on-relevance-sort-tabs
  (fn [app-state [_ tab]]
    (sort-tabs! (:windowId tab) (:data app-state))
    app-state))


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
    (let [data       (:data app-state)
          url-times  (data/track-url-time (or (:url-times data) {}) tab time (now))
          site-times (data/track-site-time (or (:site-times data) {}) tab time (now))
          new-data   (assoc data :url-times url-times :site-times site-times)]
      (console/trace time " milliseconds spent at " tab)
      (io/save new-data)
      (assoc app-state :data new-data)
      )))

(register-handler
  ::window-focus
  (fn [app-state [_ {:keys [windowId]}]]
    (console/trace "Current window" windowId)
    (let [active-tab (:active-tab app-state)
          replacing? (not= windowId (:windowId active-tab))
          is-none?   (= windowId windows/none)]
      (when is-none?
        (alarms/create window-alarm {:periodInMinutes 1}))
      (if replacing?
        (do
          (dispatch [:handle-deactivation active-tab])
          (when (not is-none?)
            (alarms/clear window-alarm)
            (go (dispatch [:handle-activation
                           (first (<! (tabs/query {:active true :windowId windowId})))])))
          (assoc app-state :active-tab nil))
        app-state)
      )
    ))


;;;;-------------------------------------
;;;; Initialization
;;;;-------------------------------------


(defn time-tracking []
  (dispatch-sync [::initialize])
  (go-loop
    [connections (runtime/on-connect)]
    (let [content (<! connections)]
      (console/log "--> Background received" (<! content))
      (>! content :background-ack)
      (recur connections)))
  )


(defn ^:export main []
  (menus/remove-all)
  (menus/create {:id       :on-relevance-show-data
                 :title    "Show Relevance data"
                 :contexts ["browser_action"]})
  (time-tracking))

(main)