;; ## Summary
;;
;; Core domain logic.
;;
(ns diffperc.core
  (:require [diffperc.app :refer :all]
            [diffperc.utils :refer :all]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
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
  (string/replace text #"[^\w\d\s]+" " "))

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

(defn diff-files
  "Returns a percentage indicating the accuracy of `test-file` against
  `base-file`.
  
  TODO: more docs"
  [base-file test-file]
  (let [num-base-file-words (count (string/split (slurp base-file) #"\n"))
        diff (:out (shell/sh "diff" base-file test-file))
        diff-lines (string/split diff #"\n")
        only-in-base-file (re-seq #"(^|\n)< .+" diff)
        only-in-test-file (re-seq #"(^|\n)> .+" diff)
        only-in-base-file-count (count only-in-base-file)
        only-in-test-file-count (count only-in-test-file)
        accuracy (- 1 (float (/ only-in-base-file-count num-base-file-words)))]
    (log :debug (str "DIFF LINES: " diff-lines))
    (log :debug (str "INCORRECT WORDS: " only-in-test-file))
    (log :info (str "NUM BASE FILE WORDS: " num-base-file-words))
    (log :info (str "Counted " (count diff-lines) " lines in diff output"))
    (log :info
         (str "NUM WORDS ONLY IN BASE (LEFT) FILE: " only-in-base-file-count))
    (log :info
         (str "NUM WORDS ONLY IN TEST (RIGHT) FILE: " only-in-test-file-count))
    (r :success
       (format "%.0f%%" (* 100 accuracy))
       {:base-file-words num-base-file-words
        :missing-words only-in-base-file-count
        :wrong-words only-in-test-file-count
        :accuracy accuracy})))
        

(defn calc-diff-perc
  "Calculate the percentage difference of words between two files.

  Returns a map describing various descrepancy measures between the base and
  test files.
  
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
            base-file-out-path (str base-file-path "--split")
            test-file-txt (slurp test-file-path)
            comparable-test-file (make-compare-friendly test-file-txt)
            test-file-out-path (str test-file-path "--split")]

        #_(log :debug (str "BASE FILE:\n" comparable-base-file))
        (log :info
             (str "Counted " (count base-file-txt) " characters in BASE file"))
        (spit base-file-out-path comparable-base-file)

        #_(log :debug (str "TEST FILE:\n" comparable-test-file))
        (log :info
             (str "Counted " (count test-file-txt) " characters in TEST file"))
        (spit test-file-out-path comparable-test-file)
        (diff-files base-file-out-path test-file-out-path)))))
