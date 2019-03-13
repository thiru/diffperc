(ns glu.core
  "Contains core/generic/miscellaneous utilities which I feel would be useful
   in most of my apps."
  (:require
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell :refer [sh]]
            [clojure.pprint :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.stacktrace]
            [clojure.string :as str]

            [aero.core :as cfg]
            [better-cond.core :as b]
            [io.aviso.exception :as ioex]
            [java-time :as jt]
            [taoensso.timbre :as timbre :refer [log spy]]

            [glu.results :refer :all]))

(def app-envs
  "Supported deployment environments for the app."
  #{:development :staging :production})

(def default-app-env :development)

(defonce
  ^{:doc "Contains the global app configuration map."}
  config
  (atom {}))

(defn P
  "Basically an alias for `pprint`."
  ([]
   (pprint *1))
  ([object]
   (pprint object))
  ([object writer]
   (pprint object writer)))

(def fmt
  "A short-form to using `cl-format` which returns a formatted string."
  (partial cl-format nil))

(defn parse-int
  "Exception-free int parsing.

   Returns the parsed integer if successful, otherwise fallback."
  [input & {:keys [fallback]
            :or {fallback 0}}]
  (try
    (Integer/parseInt input)
    (catch Exception ex
      fallback)))

(defn as-english-number
  "Print the given number (digits) in English words.

  E.g. `(as-english-number 23) ;=> \"Twenty-three\"`."
  [num]
  (cl-format nil "~@(~@[~R~]~^ ~A.~)" num))

(defn non-empty?
  "Simply the negation of `empty?`."
  [obj]
  (not (empty? obj)))

(defn case-insensitive=
  "Determine whether the given strings are equal, ignoring case."
  [string1 string2]
  (if (and (= (count string1) (count string2))
           (.equalsIgnoreCase (or string1 "") (or string2 "")))
    string1))

(defn case-insensitive-starts-with?
  "Determine whether there's a case-insensitive sub-string match."
  [full-string sub-string]
  (if (str/starts-with? (str/lower-case full-string)
                        (str/lower-case sub-string))
    full-string))

(defn has-string?
  "Determine if there's a case-insensitive match.

   * `coll`
     * The sequence of strings to search against
   * `item`
     * The string to search for
   * `test-fn`
     * An optional function to perform the 'has' test
     * By default this uses `case-insensitive=` but you may want to use
       `case-insensitive-starts-with?` instead, for example

   Returns the match, if any."
  [coll item & {:keys [test-fn]}]
  (let [test-fn (or test-fn case-insensitive=)]
    (some #(apply test-fn [% item]) coll)))

(defn as-cds
  "Converts the given list into a comma-delimited string.

   * `map-fn`
     * An optional function that transforms each item (ala `mapv`)"
  [items & {:keys [delimiter map-fn]
            :or {delimiter ", "}}]
  (fmt (str "~{~A~^" delimiter "~}")
       (if map-fn
         (mapv map-fn items)
         items)))

(defn markdownify-code
  "Wrap the given string of code in Markdown code block syntax."
  [code & {:keys [language]
           :or {language "clojure"}}]
  (str "```" language "\n" code "\n```"))

(defn dir-exists?
  "Determine whether the given path is an accessible directory.

  * `path`
    * A string specifying the file path

  Returns:
  * A `java.io.File` if the directory exists
  * `false` if the directory doesn't exist/was not found"

  [path]

  (let [dir (io/as-file path)]
    (if (and dir (.exists dir) (.isDirectory dir))
      dir
      false)))

(defn deep-merge
  "Recursively merges the given maps.

   The `merge` function that comes with Clojure only does a shallow merge.
   Nested maps are simply replaced rather than merged.

   This implementation was taken from:
   https://gist.github.com/danielpcox/c70a8aa2c36766200a95

   Returns a map."
  [& maps]
  (apply merge-with (fn [& args]
                      (if (every? map? args)
                        (apply deep-merge args)
                        (last args)))
         maps))

(defn file-exists?
  "Determine whether the file exists.

  * `path`
    * A string specifying the file path

  Returns:
  * A `java.io.File` if the file exists
  * `false` if the file doesn't exist/was not found"

  [path]

  (let [file (io/as-file path)]
    (if (and file (.exists file))
      file
      false)))
(defn file-name-sans-ext
  "Get the name of this file excluding the extension."
  [file-name]
  (b/cond
    (str/blank? file-name)
    ""

    let [ext-idx (str/last-index-of file-name ".")]

    (nil? ext-idx)
    file-name

    (subs file-name 0 ext-idx)))

(defn join-paths
  "Join the given path segments."
  [& rest]
  (.toString (apply io/file rest)))

(defn file-size
  "Get the size (in bytes) of the given path."
  [path]
  (.length (io/file path)))

(defn free-space
  "Get the number of free bytes on the specified path."
  [path]
  (.getFreeSpace (io/as-file path)))

(defn $
  "Run a shell command (via `shell/sh`).
   `:out` and `:err` is a vector of strings (split by newline chars) rather
   than a flat string, makeing it easier to read."
  [& args]
  (let [res (apply shell/sh args)]
    (assoc res
           :out (str/split (:out res) #"\n")
           :err (str/split (:err res) #"\n"))))

(defn config-updated
  "Get the last modified time of the specified config file.

   * `path`
     * Specifies the path to the config file
     * If this is blank, *config.edn* is used

   Returns a `java-time` instant object if successful, otherwise nil."
  ([] (config-updated "config.edn"))
  ([path]
   (let [path (if (str/blank? path) "config.edn" path)
         file (io/as-file path)
         file-exists (.exists file)]
     (if file-exists
       (jt/instant (.lastModified file))))))

(defn load-config
  "Load config file.

  * `profile`
    * The profile in which to load the config
    * This should be a key from `app-envs`
    * See `aero.core` for more info
  * `path`
    * An optional path to the config file"
  [& {:keys [profile path]
      :or {profile (or (:environment @config)
                       default-app-env)
           path "config.edn"}}]
  (merge (cfg/read-config path {:profile profile})
         {:environment profile
          :updated (config-updated path)}))

(defn load-config!
  "Load/reload config file.

   NOTE that this function alters the `config` var.

   See `load-config` for descriptions of args."
  [& {:keys [profile path]
      :or {profile (or (:environment @config)
                       default-app-env)
           path "config.edn"}}]
  (let [cfg (load-config :profile profile :path path)]
    (reset! config cfg)
    cfg))

(defmacro condv
  "Behaves just like `cond`, while also printing out the condition that was
  chosen. Use this while debugging/testing to easily determine which branch
  was taken in a `cond`.

  This was taken from [Eli Bendersky's website](https://eli.thegreenplace.net/2017/notes-on-debugging-clojure-code/)."
  [& clauses]
  (when clauses
    (list
     'if
     (first clauses)
     (if (next clauses)
       `(do (println (str "condv " '~(first clauses)))
            ~(second clauses))
       (throw (IllegalArgumentException.
               "cond requires an even number of forms")))
     (cons 'condv (next (next clauses))))))

(defn username
  "Get the current user's name."
  []
  (System/getProperty "user.name"))

(defn hostname
  "Get the computer's hostname."
  []
  (.. java.net.InetAddress getLocalHost getHostName))

(defn- ip-filter
  [inet]
  (and (.isUp inet)
       (not (.isVirtual inet))
       (not (.isLoopback inet))))

(defn- ip-extract
  [netinf]
  (let [inets (enumeration-seq (.getInetAddresses netinf))]
    (map #(vector (.getHostAddress %1) %2)
         (filter #(instance? java.net.Inet4Address %) inets)
         (repeat (.getName netinf)))))

(defn ips
  "Get IP addressess and network names of non-loopback, non-virtual networks
   that are up.

   The source for this function and it's helpers were take from:
   http://software-ninja-ninja.blogspot.com/2013/05/clojure-what-is-my-ip-address.html

   Returns a list of vectors, where each vector is a pair of IP address and
   network name."
  []
  (let [ifc (java.net.NetworkInterface/getNetworkInterfaces)]
    (mapcat ip-extract (filter ip-filter (enumeration-seq ifc)))))

(defn first-ip
  "Get the first IP address from `ips`.

   Returns a string IP address."
  []
  (first (first (ips))))

(defmacro catch-and-log
  "Swallow all thrown exceptions and log any errors."
  [& body]
  `(try ~@body
        (catch Exception e# (log :error e#))))

(defmacro catch-sans-log
  "Completely swallow all thrown exceptions, without even logging."
  [& body]
  `(try ~@body
        (catch Exception e#)))

(defn unexpected-error
  "Returns a standard, formatted string describing an unexpected error - e.g.
   an unhandled exception.

   * `custom-text`
     * Custom text injected into the formatted string"
  [custom-text]
  (fmt
    (str "An unexpected error occurred ~A. Please try again later, or contact "
         "the system administrator if the problem persists.")
    (or custom-text "")))

(defn stack-trace-str
  "Simply gets the stack trace of the given exception."
  [exception]
  (binding [ioex/*fonts* nil]
    (with-out-str (clojure.stacktrace/print-stack-trace exception))))
