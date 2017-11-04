;; ## Summary
;;
;; Core domain logic.
;;
(ns diffperc.core
  (:require [diffperc.app :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn calc-diffperc
  "Calculate the percentage difference of words between two files.
  
  Punctuation, white-space, and letter casing is ignored.
  
  * `base-file-path`
    * The file considered to be the base/standard against which the test is
      performed
  * `test-file-path`
    * The file being tested"
  [base-file-path test-file-path]
  (let [base-file (io/as-file base-file-path)
        test-file (io/as-file test-file-path)]
    (cond
      (not (.exists base-file))
      (println (str "Base file '" base-file-path "' not found"))

      (not (.exists test-file))
      (println (str "Test file '" test-file-path "' not found"))

      :else
      (println base-file test-file))))
