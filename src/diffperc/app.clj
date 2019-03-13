(ns diffperc.app
  "High-level app details and configuration, used in places like the CLI, web
   server defaults, build definition, etc."
  (:require
            [clojure.pprint :refer :all]

            [aero.core :as cfg]
            [io.aviso.exception :as ioex]
            [io.aviso.repl :as iorepl]
            [taoensso.timbre :as timbre :refer [log spy]]

            [glu.core :refer :all]
            [glu.fsreload :as fsreload]
            [glu.logging :refer :all]
            [glu.repl :as repl]
            [glu.results :refer :all]))

(declare
  setup-pretty-exceptions
  setup-timbre
  uncaught-exception-handler)

;; Primary Public API ---------------------------------------------------------

(defn init
  "Initialize essential app infrastructure (config, logging, etc.).

   * `environment`
     * The profile in which to load the config
     * See `aero.core` for more info
   * `merge-config`
     * An optional config map to merge into the main config
     * The original intent here is to merge in CLI options"
  [& {:keys [environment merge-config]}]
  (setup-pretty-exceptions)
  (load-config! :profile environment)
  (if (not (empty? merge-config))
    (swap! config deep-merge merge-config))
  (setup-timbre))

(defn start
  "Start the application (nREPL server, etc.)."
  []
  (log :info (fmt "Starting app..."))
  (log :info (fmt "Environment set to ~:@(~A~)" (name (:environment @config))))
  (log :info (fmt "Log level set to ~:@(~A~)"
                  (name (-> @config :logging :level))))
  (log :info
       (str "Config:\n\n"
            (markdownify-code
              (assoc-in @config [:db-spec :password] "*MASKED*"))))
  (fsreload/start-config-watch!)
  (repl/start! (:nrepl-port @config)))

(defn stop
  "Stop the application."
  []
  (fsreload/stop-config-watch!)
  (repl/stop!))

(defn restart
  "Restart the application."
  []
  (repl/restart!))

;; Primary Public API =========================================================

(defn setup-pretty-exceptions
  []
  (alter-var-root #'ioex/*app-frame-names*
                  (constantly [#"glu.*" #"diffperc.*"]))
  (iorepl/install-pretty-exceptions))

(defn setup-timbre
  []
  (timbre/handle-uncaught-jvm-exceptions! uncaught-exception-handler)
  (timbre/merge-config! (:logging @config))
  (timbre/merge-config! {:middleware [result-middleware]
                         :output-fn slim-println-output}))

(defn uncaught-exception-handler
  [throwable ^Thread thread]
  (log :error
       (str "Uncaught exception on thread: " (.getName thread) "\n\n"
            (markdownify-code throwable))))
