(ns relevance.shared-handlers
  (:require [khroma.log :as console]
            [re-frame.core :refer [register-handler]]))


(register-handler
  :log-content
  (fn [app-state [_ content]]
    (console/log "Log event:" content)
    app-state
    ))
