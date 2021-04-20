(ns utils.results
  "General facilities around reporting and validation."
  (:require ;; Clojure Core:
            [clojure.string :as str]))

(set! *warn-on-reflection* true)

;; ## Level
;; ----------------------------------------------------------------------------

(def levels
  "A generic map of levels that can be used for logging, reporting, etc.

  Negative values represent some level of failure. Non-negative values indicate
  some level of success, or just plain reporting."
  {:success 4
   :trace 3
   :info 2
   :warn 1
   :debug 0
   :error -1
   :fatal -2})

;; ## Result
;; ----------------------------------------------------------------------------

(defn result?
  "Check if the given object is a valid result map."
  [obj]
  (boolean
    (and (not (nil? obj))
         (map? obj)
         (:level obj)
         (:message obj))))

(defn r
  "Creates a map representing the result of some operation.

  I deliberately chose to use a very short function name because it will be
  used heavily throughout the codebase. Perhaps in limited cases the length of
  the name of a thing should be inversely proportional to its frequency of use?
  The other potential short name that might work is 'res', but it seems
  ambiguous in some environments (e.g. response for the web).

  * `level`
    * A value specifying the success/failure level
    * By convention, keys that map to:
      * _negative_ values are considered some level of failure
      * while _non-negative_ values are considered informational or successful
  * `message`
    * A message describing the result
  * `rest`
    * Additional key/value pairs to merge into the result map"
  ([level]
   (r level ""))
  ([level message]
   {:level level :message message})
  ([level message & {:as rest}]
   (merge (r level message) rest)))

;; We'll just use a simple function to determine success in ClojureScript, as
;; I couldn't get the multi-method implementation working.
#?(:cljs
   (defn success?
     "Determine whether the given object represents a successful outcome."
     [obj]
     (cond
       (nil? obj)
       false

       (= js/Error (type obj))
       false

       (= PersistentArrayMap (type obj))
       (if (result? obj)
         (<= 0 (get levels (:level obj) (:error levels)))
         true)

       ;; Default to Javascript truthiness
       :else
       (if obj true false))))

#?(:clj
   (defmulti success?
     "Determine whether the given object represents a successful outcome.

     `obj` is considered successful in all cases except the following:

     * `nil`
     * `false`
     * An instance of `Throwable`
     * A result map where the value of `:level` is a keyword defined in
       `glu.results/levels` which maps to a negative number"
     class))

#?(:clj
   (defmethod success? nil nil-type [_]
     false))

#?(:clj
   (defmethod success? boolean boolean-type [bool]
     bool))

#?(:clj
   (defmethod success? Throwable throwable-type [_]
     false))

#?(:clj
   (defmethod success? clojure.lang.PersistentArrayMap map-type [maybe-r]
     (if (result? maybe-r)
       (<= 0 (get levels (:level maybe-r) (:error levels)))
       true)))

#?(:clj
   (defmethod success? :default [_]
     true))

(defn failed?
  "Determine whether the given object represents a failure outcome.

  This is basically the opposite of `success?`."
  [obj]
  (not (success? obj)))

(defn warned?
  "Determine whether the given object represents a warning or failure outcome.

  This is basically the same as `failed?` except also returns true if `obj`
  is a result map where `:level` is `:warn`."
  [obj]
  (or (failed? obj)
      (= :warn (:level obj))))

(defn parse-sh
  "Parse the given shell command (i.e. `clojure.java.shell/sh`).

   * `cmd-res`
     * The returned (map) from `clojure.java.shell/sh`
   * `throw?`
     * Whether to throw an exception if the command failed

   Return:
   * throws an exception if `cmd-res` in `nil`
   * when `throw?` is truthy
     * a result map with the following additional keys:
       * `:exit`
         * The exit code of the command
     * throws an `ExceptionInfo`
       * with concatenated standard out and error from the command in the
         message
       * The map contains the exit code of the command in `:exit`"
  [cmd-res & {:keys [throw?]}]
  (if (nil? cmd-res)
    (throw (ex-info "No command output given" {}))
    (let [exit-code (:exit cmd-res)
          msg (str (:out cmd-res)
                   (if (empty? (:err cmd-res))
                     ""
                     (str " " (:err cmd-res))))]
      (if (and throw? (not= 0 exit-code))
        (throw (ex-info msg {:exit exit-code}))
        (r (if (= 0 exit-code)
             :success
             :error)
           msg
           :exit exit-code)))))

