(ns relevance.reagent-utils
  (:require [reagent.core :as reagent]))

(def initial-focus-wrapper
  (with-meta identity
             {:component-did-mount #(.focus (reagent/dom-node %))}))