(ns diffperc.main
  "Command-line interface and entry-point into the application.

   Command-line arguments are parsed and displayed with the help of
   [clojure.tools.cli](https://github.com/clojure/tools.cli)."
  (:require
            [clojure.set :as sets]
            [clojure.string :as string]
            [clojure.tools.cli :as cli]

            [rebel-readline.main :as rebel]
            [taoensso.timbre :as timbre :refer [log spy]]

            [glu.core :refer :all]
            [glu.repl :as repl]
            [glu.results :refer :all]

            [diffperc.app :as app]
            [diffperc.core :as core])
  (:gen-class))

(def startup-options
  "The command-line options the app was started with (intended for debugging)."
  nil)

(def env-opt
  "Specification for the application environment CLI option (used by
   `clojure.tools.cli`).

   This CLI option is separated from the other options (see `cli-options`)
   because it needs to be parsed first and independently. The reason for this
   is that some of the other options need to know the config defaults, which
   isn't known until the application environment is determined.  After this,
   the remaining options can be parsed and handled appropriately."

  ["-e" "--environment ENVIRONMENT"
   :desc (fmt "Environment in which the application is running (~A)"
              (as-cds app-envs :map-fn name))
   :default default-app-env
   :default-desc (name default-app-env)
   :parse-fn #(keyword
                (has-string?
                  (map name app-envs)
                  %
                  :test-fn case-insensitive-starts-with?))
   :validate [#(contains? app-envs %)
              (str "Environment must be one of: "
                   (as-cds app-envs :map-fn name))]])

(defn cli-options
  "Get a vector of CLI options.

   Each item follows the spec of a CLI option as defined by
   `clojure.tools.cli`."

  [config]

  (vec
    (concat
      [env-opt]
      [["-p" "--web-server-port PORT"
        :desc "Web server listen port"
        :default (:web-server-port config)
        :parse-fn #(Integer/parseInt %)
        :validate [#(< 0 % 0x10000)
                   "Port must be a number between 0 and 65536"]]

       ["-r" "--nrepl-port PORT"
        :desc "nREPL server listen port"
        :default (:nrepl-port config)
        :parse-fn #(Integer/parseInt %)]

       ["-l" "--log-level LEVEL"
        :desc (fmt "Log verbosity level (~A)"
                   (as-cds timbre/-levels-vec :map-fn name))
        :default (-> config :logging :level)
        :default-desc (name (-> config :logging :level))
        :parse-fn #(first (find levels (keyword %)))
        :validate [#(get levels %)
                   (str "Log verbosity level must be one of: "
                        timbre/-levels-set)]]

       ["-v" "--version" "Show app version"]

       ["-h" "--help" "Show help"]])))

(defn usage
  "User-friendly CLI usage description.

  * `options-summary`
    *  A user-friendly summary of CLI options to be injected into the full
       summary string returned
    *  We generate the options summary with `clojure.tools.cli/parsed-opts`
  * `config`
    * The app config map"
  [options-summary config]
  (->> [(str "diffperc " (:version config))
        ""
        (:description config)
        ""
        "Usage: diffperc [options] base-file test-file"
        ""
        "Options:"
        options-summary
        ""]
       (string/join \newline)
       (string/trim)))

(defn validate-cli-args
  "Validate the given command-line arguments.

   * `args`
     * The list of command-line args provided by the user
   * `config`
     * The app config map

   Returns a result map with the following additional kvps:

   * `:files`
     * A list of files to compare
   * `:options`
     * A map of effective/parsed options
     * See `cli-options` for a specfication of supported options"

  [args config]

  (let [parsed-opts (cli/parse-opts args (cli-options config))
        {:keys [options arguments errors summary]} parsed-opts]
    (cond
      ;; No arguments were given
      (empty? args)
      (r :success
         ""
         :options options)

      ;; Help option was specified
      (:help options)
      (r :info
         (usage summary config)
         :options options)

      ;; Version option was specified
      (:version options)
      (r :info
         (:version config)
         :options options)

      ;; Errors were found while parsing
      errors
      (r :error
         (string/join \newline errors)
         :options options)

      ;; Correct number of arguments were given
      (= 2 (count arguments))
      (r :success
         ""
         :files arguments
         :options options)

      :else
      (r :success
         ""
         :options options))))

(defn parse-app-env
  "Parse and verify the application environment option.

   * `args`
     * A list of command-line arguments provided by the user

   Returns a result map with the parsed environment keyword (a value from
   `app-envs`) under the `:val` key."
  [args]
  (let [args (vec args)
        matched-indeces (keep-indexed
                          (fn [idx val]
                            (if (has-string? ["--environment" "-e"] val)
                              idx))
                          args)
        env-opt-idx (last matched-indeces)
        env-opt-specified? (and env-opt-idx
                                (<= 0 (last matched-indeces))
                                (< env-opt-idx (count args)))
        env-opt-args (if env-opt-specified?
                       (subvec args env-opt-idx (+ 2 env-opt-idx)))
        parsed-opt (if env-opt-specified?
                     (cli/parse-opts env-opt-args [env-opt]))]
    (cond
      (not env-opt-specified?)
      (r :success
         "User did not specify --environment option"
         :val default-app-env)

      ;; This shouldn't be possible but let's protect against it anyway
      (empty? parsed-opt)
      (throw (ex-info (str "Unexpected error parsing the --environment "
                           "option. The returned map (from `cli/parse-opts` "
                           "was empty.")
                      {:args args}))

      ;; Errors were found while parsing
      (:errors parsed-opt)
      (r :error
         (string/join \newline (:errors parsed-opt))
         :val default-app-env)

      :else
      (r :success
         "Found user-supplied --environment option"
         :val (-> parsed-opt :options :environment)))))

(defn exit
  "Exit app with the given result map, printing the message to standard out."
  [result]
  (binding [*out* (if (failed? result)
                    *err*
                    *out*)]
    (println (:message result)))
  (if (failed? result)
    (log :error (:message result)))
  (System/exit
    (cond
      (failed? result) 1
      :else 0)))

(defn -main
  "This is the entry-point into the application (e.g. when run from the
   command-line.

   * `args`
     * A list of command-line arguments provided by the user
     * Each argument is a string"

  [& args]

  (let [app-env-r (parse-app-env args)]

    (if (failed? app-env-r)
      (exit app-env-r))

    (let [parsed-cli-r (validate-cli-args args (load-config))
          cli-options (:options parsed-cli-r)
          ;; Account for log level option being in a nested map
          cli-options (-> cli-options
                          (dissoc :log-level)
                          (assoc-in [:logging :level] (:log-level cli-options)))]

      (alter-var-root #'startup-options (constantly cli-options))

      (app/init :environment (:val app-env-r)
                :merge-config cli-options)

      ;; Debug CLI startup
      ;(println (fmt "User-provided CLI arguments: ~A" args))
      ;(println (fmt "Effective CLI options: ~A" cli-options))

      (if (or (failed? parsed-cli-r))
        (exit parsed-cli-r))

      (cond
        (= 2 (count (:files parsed-cli-r)))
        (let [res (core/calc-diff-perc (first (:files parsed-cli-r))
                                       (second (:files parsed-cli-r)))]
          (exit res))
        :else ; Start REPL
        (do
          (app/start)
          (rebel/-main) ; Blocking call
          (repl/stop!)
          (System/exit 0))))))
