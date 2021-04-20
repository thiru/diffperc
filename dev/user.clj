(ns user
  "Initial namespace loaded when using a REPL (e.g. using `clj`)."
  (:require ;; Clojure Core:
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.pprint :refer :all]
            [clojure.reflect :refer :all]
            [clojure.repl :refer :all]
            [clojure.string :as str]

            ;; Third-Party:
            [reloader.core :as reloader]
            [repl-base.core :as repls]

            ;; Our Utils:
            [utils.results :as r]

            ;; Our Domain:
            [diffperc.core :as c]))

(defonce started? (atom false))

(when (not @started?)
  (reset! started? true)
  (reloader/start ["src" "dev"])
  (repls/start))

