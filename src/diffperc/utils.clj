;; ## Summary
;;
;; Common utilities used throughout the project.
;;
;; This namespace does not contain any domain-specific code.
;;
(ns diffperc.utils
 (:require [clojure.string :as string]))

;; ## Functions

(defn as-english-number
  "Print the given number in English words.

  E.g. `(as-english-number 23) ;=> \"Twenty-three\"`."
  [num]
  (clojure.pprint/cl-format nil "~@(~@[~R~]~^ ~A.~)" num))

(defn trim-version
  "Trims version strings of trailing segments containing only zeroes.

  E.g. `(trim-version \"0.1.0\") ;=> \"0.1\"`."
  [version]
  (string/replace version #"(\.0+)+$" ""))
