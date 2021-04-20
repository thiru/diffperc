(ns diffperc.main
  "Command-line interface and entry-point into the application."
  (:require ;; Clojure Core:
            [clojure.string :as str]

            ;; Our Utils:
            [utils.results :as r]

            ;; Our Domain:
            [diffperc.core :as core])
  (:gen-class))

(set! *warn-on-reflection* true)

(def usage "Usage: diffperc base-file test-file")

(defn -main
  "This is the entry-point into the application (e.g. when run from the
   command-line.

   * `args`
     * A list of command-line arguments provided by the user
     * Each argument is a string

  Returns 0 on success, otherwise a positive integer."
  [& args]
  (when (not= 2 (count args))
    (println usage)
    (System/exit 1))

  (let [result (core/calc-diff-perc (first args)
                                    (second args))]
    (when (r/failed? result)
      (println (:message result))
      (System/exit 1))

    (println (:message result))
    (System/exit 0)))
