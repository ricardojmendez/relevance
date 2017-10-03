(ns relevance.shared-handlers
  (:require [khroma.log :as console]
            [re-frame.core :refer [reg-event-fx]]))


(reg-event-fx
  :log-content
  (fn [_ [_ content]]
    (console/log "Log event:" content)
    nil
    ))
