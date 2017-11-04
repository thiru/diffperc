;; ## Summary
;;
;; Common utilities used throughout the project.
;;
;; This namespace does not contain any domain-specific code.
;;
(ns diffperc.utils
 (:require [clojure.string :as string]))

;; ## String Utils

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

;; ## Result Utils

(def levels
  "A generic map of levels that can be used for logging, reporting, etc.
  
  Negative values represent some level of failure. Non-negative values indicate
  some level of success, or just plain reporting."
  {:success 2
   :info 1
   :debug 0
   :warning -1
   :error -2
   :fatal -3})

(defn r
  "A map representing the result of some operation, including the level of
  success/failure, and an optional data item.
  
  TODO: more docs"
  ([] (r :info))
  ([level] (r level ""))
  ([level msg] {:level level :message msg})
  ([level msg data] {:level level :message msg :data data}))

(defn success?
  "Determine whether the given object represents a successful outcome.
  
  TODO: more docs"
  [obj]
  (cond
    (nil? obj)
    false

    (false? obj)
    false
    
    (map? obj)
    (let [level (:level obj)]
      (cond
        (number? level)
        (<= 0 level)
        
        (keyword? level)
        (<= 0 (get levels level (:error levels)))))
    
    :else
    true))

(defn failed?
  "Determine whether the given object represents a failure outcome.
  
  TODO: more docs"
  [obj]
  (not (success? obj)))
