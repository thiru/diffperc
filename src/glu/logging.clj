(ns glu.logging
  (:require
            [clojure.string :as str]

            [better-cond.core :as b]
            [clansi]
            [io.aviso.exception :as ioex]
            [java-time :as jt]
            [taoensso.timbre :as timbre :refer [log spy]]

            [glu.core :refer :all]
            [glu.email :as email]
            [glu.results :refer :all]))

(declare send-log-to-email)

(defonce cached-ip
  (delay (first-ip)))

(defonce cached-username
  (delay (username)))

(defn colourize
  "Colourize the given text at the specified level (should be from `levels`).

   Returns a colourized string."
  [text level]
  (condp = level
    :success
    (clansi/style text :green)

    :info
    (clansi/style text :bright)

    :trace
    (clansi/style text :blue :underline)

    :debug
    (clansi/style text :blue)

    :warn
    (clansi/style text :yellow)

    :error
    (clansi/style text :red)

    :fatal
    (clansi/style text :bg-red)

    :else
    (clansi/style text :default)))

(defn result-middleware
  "Custom middleware to handle printing of result maps better."
  [data]
  (b/cond
    let [first-arg (first (:vargs data))]

    (not (result? first-arg))
    data

    let [result first-arg
         extra-data (dissoc result :level :message)
         new-vargs (if (empty? extra-data)
                     [(:message result)]
                     [(:message result) extra-data])
         data (assoc data :vargs new-vargs)]

    data))

(defn slim-println-output
  "A slimmer log output based on Timbre's default with the following
   differences:

   * Doesn't include a time stamp
   * Doesn't include the hostname
   * The log level is colourized and padded
   * The bracketed namespace and line number is colourized"
  ([data] (slim-println-output nil data))
  ([opts data]
   (let [{:keys [no-stacktrace? stacktrace-fonts]} opts
         {:keys [level ?err #_vargs msg_ ?ns-str ?file hostname_
                 timestamp_ ?line]} data]
     (str
       (colourize (fmt "~5@<~A~>" (str/upper-case (name level))) level)
       (clansi/style
         (str " [" (or ?ns-str ?file "?") ":" (or ?line "?") "] - ")
         :magenta)
       (force msg_)
       (when-not no-stacktrace?
         (when-let [err ?err]
           (str "\n" (timbre/stacktrace err opts))))))))

(defn email-appender
  "Log appender that emails errors (via Mailgun)."
  []
  {:enabled? true
   :async? true
   :min-level :error
   :rate-limit [[10 5000]] ; limit 5 calls/sec
   :output-fn :inherit
   :fn (fn [data] (send-log-to-email data))})

(defn send-log-to-email
  "Send email of given log data."
  [data]
  (let [msg (str (force (:msg_ data)))]
    (email/send-email-r (r (:level data)
                           msg))))
