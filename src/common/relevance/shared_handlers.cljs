(ns relevance.shared-handlers
  (:require [khroma.log :as console]
            [re-frame.core :refer [reg-event-db]]))


(reg-event-db
  :log-content
  (fn [app-state [_ content]]
    (console/log "Log event:" content)
    app-state
    ))
