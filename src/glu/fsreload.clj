(ns glu.fsreload
  "Support automatic reloading of Clojure code by monitoring file-system
   change events.
   Note: this requires the hawk (https://github.com/wkf/hawk) and ns-tracker
   (https://github.com/weavejester/ns-tracker) libraries."
  (:require
            [clojure.data :as data]
            [clojure.string :as string]

            [hawk.core :as hawk]
            [ns-tracker.core :as nst]
            [taoensso.timbre :as timbre :refer [log spy]]

            [glu.core :refer :all]))

(def watch-dir
  "The directory to watch for source code changes."
  "src")

(def modified-namespaces
  "Holds modified namespaces."
  (nst/ns-tracker [watch-dir]))

(defn reload!
  "Reload modified namespaces.

  * `fs-event`
    * A map describing the file-system event that occurred"
  [fs-event]
  (let [namespaces (modified-namespaces)]
    (when (and (:file fs-event) (pos? (count namespaces)))
      (log :debug
           (fmt "Source file (~A): \"~A\""
                (string/upper-case (name (:kind fs-event)))
                (.getPath (:file fs-event))))
      (doseq [ns-sym namespaces]
        (log :trace (str "(require " ns-sym " :reload)"))
        (require ns-sym :reload)))))

(defonce
  ^{:doc "Holds the file-system watcher instance."}
  watcher
  (atom nil))

(defonce
  ^{:doc "Holds the config watcher instance."}
  config-watcher
  (atom nil))

(defn watcher-filter
  "Filter the type of file-system events we're interested in."
  [_ctx {:keys [kind file]}]
  (and file
       ;; We're only interested in file modified events. I don't think we need
       ;; to care about file deleted events as we can't unload the code anyway
       ;; (as far as I know). And file created events don't seem important
       ;; enough to bother watching.
       (= :modify kind)
       ;; Ignore directory events
       (.isFile file)
       (not (.isHidden file))
       (let [file-name (.getName file)]
         ;; Ignore hidden/temporary files
         (and (not= \. (first file-name))
              (not= \# (first file-name))
              (not= \~ (last file-name))
              ;; Only interested in Clojure file types
              (or (string/ends-with? file-name "clj")
                  (string/ends-with? file-name "cljc")
                  (string/ends-with? file-name "cljs"))))))

(defn config-watcher-filter
  "Filter the type of file-system events we're interested in while watching the
   config file."
  [_ctx {:keys [kind file]}]
  (and file
       ;; We're only interested in file modified events
       (= :modify kind)))

(defn watcher-handler
  "Handler for the Hawk file-system watcher.
  This performs the code reloading."
  [ctx e]
  (reload! e)
  ctx)

(defn config-watcher-handler
  "Handler for the Hawk file-system watcher.
  This performs the config reloading."
  [ctx e]
  (log :info (fmt "Config file (~A) updated"
                  (.getName (:file e))))
  (let [old-config @config]
    (load-config!)
    (let [new-config @config
          diff (data/diff new-config old-config)
          new-only (first diff)
          old-only (second diff)]
      (log :info
           (fmt (str "Config file differences:~%"
                     "ONLY IN NEW CONFIG: ~A~%"
                     "ONLY IN OLD CONFIG: ~A")
                (dissoc (first diff) :updated)
                (dissoc (second diff) :updated)))))
  ctx)

(defn start-watch!
  "Start watching the folder specified by `watch-dir` for file change
   notifications."
  []
  (if-not (nil? @watcher)
    (log :warn (str "File-system watcher already appears to be running - "
                    "not starting a new watcher"))
    (do
      (reset! watcher
              (hawk/watch! [{:paths [watch-dir]
                             :filter watcher-filter
                             :handler watcher-handler}]))
      (log :info
           (fmt "File-system watcher started watching '~A'" watch-dir)))))

(defn start-config-watch!
  "Start watching the configuration file for changes."
  []
  (if-not (nil? @config-watcher)
    (log :error (str "Config file watcher already appears to be running - "
                     "not starting a new config watcher"))
    (do
      (reset! config-watcher
              (hawk/watch! [{:paths ["config.edn"]
                             :filter config-watcher-filter
                             :handler config-watcher-handler}]))
      (log :info "Config watcher started watching 'config.edn'"))))

(defn stop-watch!
  "Stop the file-system watcher of source files."
  []
  (if (nil? @watcher)
    (log :error "File-system watcher doesn't appear to be running")
    (do
      (hawk/stop! @watcher)
      (log :info "File-system watcher stopped"))))

(defn stop-config-watch!
  "Stop the file-system watcher of the config file."
  []
  (if (nil? @config-watcher)
    (log :error "Config file watcher doesn't appear to be running")
    (do
      (hawk/stop! @config-watcher)
      (log :info "Config file watcher stopped"))))

