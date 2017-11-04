;; ## Summary
;;
;; CLI and entry-point into the application.
;;
;; Command-line arguments are parsed and displayed with the help of
;; [clojure.tools.cli](https://github.com/clojure/tools.cli).
;;
(ns diffperc.main
 (:require [diffperc.app :refer :all]
           [diffperc.utils :refer :all]
           [diffperc.core :refer :all]
           [clojure.set :as sets]
           [clojure.string :as string]
           [clojure.tools.cli :as cli])
 (:gen-class))

;; ## Values

(def cli-options
  "A vector of CLI options. Each item follows the spec of a CLI option as
  defined by `clojure.tools.cli`."
  [["-l" "--log-level LEVEL" "Log verbosity level"
    :default (:log-level-default app-config)
    :parse-fn #(Integer/parseInt %)]
   ["-v" "--version" "Show app version"]
   ["-h" "--help" "Show help"]])

;; ## Functions

(defn usage
  "User-friendly CLI usage description.

  * `options-summary`
    *  A user-friendly summary of CLI options to be injected into the full
       summary string returned
    *  We generate the options summary with `clojure.tools.cli/parsed-opts`"
  [options-summary]
  (->> [(str "diffperc " (trim-version (:version app-info)))
        ""
        (:description app-info)
        ""
        "Usage: diffperc [options] base-file test-file"
        ""
        "Options:"
        options-summary]
        
       (string/join \newline)
       (string/trim)))

(defn error-msg
  "Common error message format.
 
  * `errors`
    * A vector of error messages"
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn validate-args
  "Validate the given command-line arguments.
  A map is returned of the following form:

  * `:ok?`
    * Whether the validation was successful or not
  * `:exit-message`
    * A message to be printed on exiting the app
    * This could contain error messages and/or the usage summary
  * `:files`
    * A seq of files to compare"
  [args]
  (let [parsed-opts (cli/parse-opts args cli-options)
        {:keys [options arguments errors summary]} parsed-opts]
    (cond
      ;; Help option was specified
      (:help options)
      {:exit-message (usage summary) :ok? true}

      ;; Version option was specified
      (:version options)
      {:exit-message (trim-version (:version app-info)) :ok? true}

      ;; Errors were found while parsing
      errors
      {:exit-message (error-msg errors)}

      ;; No files given
      (zero? (count arguments))
      {:exit-message (usage summary)}

      ;; Need exactly two files
      (not= 2 (count arguments))
      {:exit-message "Exactly two files are expected"}

      ;; All is good. Run program!
      (= 2 (count arguments))
      {:files arguments :options options :ok? true}

      ;; Failed custom validation. Exit with usage summary.
      :else
      {:exit-message (usage summary)})))

(defn exit
  "Exit app with code `status` and print `msg` to standard out."
  [status msg]
  (println msg)
  (System/exit status))

(defn -main
  "This function is called when the **app first starts up**.
  
  * `args`
    * A vector of command-line arguments"
  [& args]
  (let [{:keys [files options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (calc-diffperc (first arguments) (second arguments)))))
