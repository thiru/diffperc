(ns user
  "Initial namespace loaded when using a REPL (e.g. using `clj`)."
  (:require
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.pprint :refer :all]
            [clojure.reflect :as reflect]
            [clojure.repl :refer :all]
            [clojure.string :as str]

            [better-cond.core :as b]
            [java-time :as jt]
            [taoensso.timbre :as timbre :refer [log spy]]

            [glu.core :refer :all]
            [glu.fsreload :as reload]
            [glu.repl :as repl]
            [glu.results :refer :all]

            [diffperc.core :refer :all]))

(defonce fswatch-started?
  (reload/start-watch!))
