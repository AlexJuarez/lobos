(defproject lobos "0.7.0-SNAPSHOT"
  :description
  "A library to create and manipulate SQL database schemas."
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]]
  :dev-dependencies [[clojureql "1.1.0-SNAPSHOT"]
                     [swank-clojure "1.2.1"]
                     [lein-clojars "0.6.0"]
                     [marginalia "0.5.0-alpha"]
                     [cljss "0.1.1"]
                     [hiccup "0.3.1"]
                     [clj-help "0.2.0"]]
  :jar-exclusions [#"www.clj"])
