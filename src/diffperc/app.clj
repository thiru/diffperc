;; ## Summary
;;
;; High-level properties of the project, used in places like the CLI, web
;; server defaults, build definition, etc.
;;
(ns diffperc.app)

;; ## Values

(def app-info
  "High-level app info/metadata. These properties are more on the descriptive
  side and do not affect the state or behaviour of the app in any meaningful
  way."
  {:version "0.1.0"
   :description (str "DiffPerc calculates the percentage difference of words "
                     "between two text files. Punctuation is ignored.")})

(def app-config
  "App configuration and defaults.

  We log at the most verbose level by default. This isn't good for production,
  but it makes it easy for development (where we typically want the most
  verbose logging, and we can easily rebind in production mode when `-main`
  runs (i.e. via `with-redefs`)."
  {:log-level :debug})
