(ns glu.email
  (:require
            [clojure.string :as str]

            [better-cond.core :as b]
            [markdown.core :as md]
            [org.httpkit.client :as http]
            [taoensso.timbre :as timbre :refer [log spy]]

            [glu.core :refer :all]
            [glu.results :as r]))

(defn send-email
  "Sends an HTML email.

   * `subject`
     * The subject of the email
   * `body`
     * markdown string that will be converted to HTML"
  [subject body & {:keys [from to cc bcc include-server-summary?]
                   :or {from (-> @config :email :from)
                        to (-> @config :email :to)
                        cc (-> @config :email :cc)
                        bcc (-> @config :email :bcc)}}]
  (b/cond
    (str/blank? from)
    (log :error "No from address provided")

    (str/blank? to)
    (log :error "No to address provided")

    let [url (-> @config :email :api-base-url)]

    (str/blank? url)
    (log :error (fmt "No email service URL defined in config.edn in key: ~A"
                     {:email :api-base-url}))

    let [url (str url "/messages")]

    let [opts {:basic-auth ["api" (-> @config :email :api-key)]
               :form-params {:from from
                             :to to
                             :subject subject
                             :html (md/md-to-html-string body)}}]

    @(http/post url opts)))

(defn send-email-r
  "Sends an HTML email from the given result map.

   * `result`
     * `level`
       * This will be included in the subject
     * `message`
       * A markdown string that will be converted to HTML"
  [result & {:keys [subject from to cc bcc]
             :or {from (-> @config :email :from)
                  to (-> @config :email :to)
                  cc (-> @config :email :cc)
                  bcc (-> @config :email :bcc)}}]
  (b/cond
    (empty? result)
    (log :error "No result map provided")

    let [subject (or subject
                     (fmt "~A - ~A"
                          (-> @config :name)
                          (-> result :level name str/capitalize)))]

    (send-email subject
                (:message result)
                :from from
                :to to
                :cc cc
                :bcc bcc)))
