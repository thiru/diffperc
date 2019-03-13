(ns glu.results
  "General facilities around reporting and validation."
  (:require
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

;; ## Level --------------------------------------------------------------------

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

(s/def ::level #(contains? levels %))

(defn level->log-level
  "Account for differences between `levels` and Timbre's logging levels.

   * `level`
     * Expected to be a value from `levels`

   Returns a log level usable by Timbre."
  [level]
  (cond
    (nil? level)
    :error

    (= :success level)
    :info

    :else
    level))

(defn r->ll
  "Get a Timbre-compatible log level from the given result map."
  [result]
  (level->log-level (:level result)))

;; Level =======================================================================

;; ## Simple Result ------------------------------------------------------------

;; Let's allow a message to be any type, since we can usually get a meaningful
;; string representation of most objects.
(s/def ::message any?)

(s/def ::result
  (s/keys :req-un [::level ::message]))

(s/fdef r
        :args (s/cat :level ::level
                     :message (s/? ::message)
                     :more (s/? map?))
        :ret ::result)

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
   (defn success? [obj]
     (cond
       (nil? obj)
       false

       (= js/Error (type obj))
       false

       (= PersistentArrayMap (type obj))
       (if (s/valid? ::result obj)
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
     (if (s/valid? ::result maybe-r)
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

;; Simple Result ===============================================================

;; ## Meta Result --------------------------------------------------------------

(s/fdef mr
        :args (s/cat :level ::level
                     :message ::message
                     :data #(instance? clojure.lang.IMeta %)
                     :more (s/? map?)))

(defn mr
  "The name of this function is an acronym for 'meta result'.

  It associates a result map (via `(r)`) to the metadata of `data` under a key
  named `:result`. Existing metadata on `data` is preserved.

  * `data`
    * The object with which to associate the metadata
    * Note that only certain types of objects can have metadata
      * I.e. only objects that implement `clojure.lang.IMeta`

  Returns `data`.

  Please see `(r)` for documentation on parameters not documented here, and
  other rationale."
  ([level message data]
   (vary-meta data assoc :result (r level message)))
  ([level message data & {:as rest}]
   (vary-meta data assoc :result (merge (r level message) rest))))

(defn get-mr
  "Get the result map attached to `obj` as metadata."
  [obj]
  (if (and obj #(instance? clojure.lang.IMeta obj))
      (-> obj meta :result)))

(defn msuccess?
  "Same as `success?` except check the metadata of `obj` for a key named
  `:result`, passing it's value to `success?`."
  [obj]
  (success? (:result (meta obj))))

(defn mfailed?
  "Same as `failed?` except check the metadata of `obj` for a key named
  `:result`, passing it's value to `failed?`."
  [obj]
  (failed? (:result (meta obj))))

;; Meta Result =================================================================
