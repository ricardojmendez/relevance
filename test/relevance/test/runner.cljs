(ns relevance.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [relevance.test.data]
            [relevance.test.migrations]
            [relevance.test.utils]))



(enable-console-print!)

(doo-tests 'relevance.test.data
           'relevance.test.migrations
           'relevance.test.utils)

