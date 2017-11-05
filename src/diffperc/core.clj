;; ## Summary
;;
;; Core domain logic.
;;
(ns diffperc.core
  (:require [diffperc.app :refer :all]
            [diffperc.utils :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn strip-subtitle-markup
  "TODO: docs"
  [text]
  (->
    ;; Remove speaker marker (e.g. "M:")
    (string/replace text #"(^|\n\s*)[\w\d]+:\s+" "")
    ;; Remove enviromental queues (e.g. "[laughter]")
    (string/replace #"\[.+\]" "")))

(defn strip-punctuation
  "TODO: docs"
  [text]
  (string/replace text #"[^\w\d\s]" ""))

(defn one-word-per-line
  "TODO: docs"
  [text]
  (->
    (string/replace text #"\s" "\n")
    ;; Collapse multiple new lines
    (string/replace #"\n\n+" "\n")))

(defn make-compare-friendly
  "TODO: docs"
  [text]
  (->
    (strip-subtitle-markup text)
    strip-punctuation
    one-word-per-line
    string/trim
    string/lower-case))

(defn calc-diff-perc
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
      (r :error (str "Base file '" base-file-path "' not found"))

      (not (.exists test-file))
      (r :error (str "Test file '" test-file-path "' not found"))

      :else
      (let [base-file-txt (slurp base-file-path)
            comparable-base-file (make-compare-friendly base-file-txt)
            test-file-txt (slurp test-file-path)
            comparable-test-file (make-compare-friendly test-file-txt)]
        (log :debug "BASE FILE:")
        (log :debug comparable-base-file)
        (log :debug "TEST FILE:")
        (log :debug comparable-test-file)))))
