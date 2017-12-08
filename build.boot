;; ## Boot Build Definition

;; See https://github.com/boot-clj/boot for details on using Boot.

;; We're pulling out the dependencies into a global var since it'll be used in
;; at least two places. One for defining the Boot environment, and for
;; dynamically generating a project.clj file (see below).
;;
(def deps
 "Clojure library dependencies used by this project."
 '[[org.clojure/clojure "1.8.0"]
   [org.clojure/tools.cli "0.3.5"]])

;; Establish Boot environment.
;;
(set-env!
 :resource-paths #{"src"}
 :dependencies deps)

;; High-level project metadata has been factored out into it's own namespace,
;; `diffperc.app` so it can be referred to from multiple namespaces.
;;
(require '[diffperc.app :refer :all])

;; Define project metadata, etc.
;;
(task-options!
  pom {:project 'diffperc
       :version (:version app-info)
       :description (:description app-info)}
  aot {:namespace '#{diffperc.main}}
  jar {:main 'diffperc.main}
  sift {:include #{#"\.jar$"}})

;; Require main project namespaces so they're immediately available when
;; running the REPL.
;;
(require '[diffperc.main :as main]
         '[diffperc.utils :refer :all]
         '[diffperc.core :refer [calc-diff-perc]]
         '[clojure.java.shell :as shell])

(deftask docs
  "Generate (Literate Programming) documentation using Marginalia.
  The existing Boot plugin for Marginalia doesn't seem to work so what we're
  doing here is dynamically generating a Leiningen project.clj file which the
  original Marginalia plugin then uses (via `lein marg`)."
  []
  (println "Creating Leiningen project.clj...")
  (spit "project.clj"
        (format "(defproject diffperc \"%s\"
                :description \"%s\"
                :dependencies %s
                :plugins [[lein-marginalia \"0.9.0\"]]
                :main ^:skip-aot diffperc.main
                :target-path \"target/%%s\"
                :profiles {:uberjar {:aot :all}})\n"
                (:version app-info)
                (:description app-info)
                deps))
  (let [cmd "lein marg --file index.html"]
   (println "Running" cmd "...")
   (shell/sh "sh" "-c" cmd)))

(deftask run
  "Start the app with default settings."
  []
  (comp
    (with-pass-thru _
      (main/-main "run"))))

(deftask build
  "Build a stand-alone jar."
  []
  (comp
    (aot)
    (pom)
    (uber)
    (jar)
    (sift)
    (target)))
