;; Copyright (c) Nicolas Buduroi. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 which can be found in the file
;; epl-v10.html at the root of this distribution. By using this software
;; in any fashion, you are agreeing to be bound by the terms of this
;; license.
;; You must not remove this notice, or any other, from this software.

(ns lobos.test
  (:refer-clojure :exclude [alter defonce drop])
  (:use clojure.test
        (clojure.contrib [io :only [delete-file file]])
        (lobos connectivity core utils))
  (:import (java.lang UnsupportedOperationException)))

;;;; DB connection specifications

(def h2-spec
  {:classname   "org.h2.Driver"
   :subprotocol "h2"
   :subname     "./lobos"})

(def mysql-spec
  {:classname   "com.mysql.jdbc.Driver"
   :subprotocol "mysql"
   :user        "lobos"
   :password    "lobos"
   :subname     "//localhost:3306/"})

(def postgresql-spec
  {:classname   "org.postgresql.Driver"
   :subprotocol "postgresql"
   :user        "lobos"
   :password    "lobos"
   :subname     "//localhost:5432/lobos"})

(def sqlite-spec
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     "./lobos.sqlite3"
   :create      true})

(def sqlserver-spec
  {:classname    "com.microsoft.sqlserver.jdbc.SQLServerDriver"
   :subprotocol  "sqlserver"
   :user         "lobos"
   :password     "lobos"
   :subname      "//localhost:1433"
   :databaseName "lobos"})

(def db-specs [h2-spec
               mysql-spec
               postgresql-spec
               sqlite-spec
               sqlserver-spec])

(defn driver-available? [db-spec]
  (try
    (clojure.lang.RT/classForName (:classname db-spec))
    true
    (catch ClassNotFoundException e false)))

(def available-specs (filter driver-available? db-specs))

(defn available-global-cnx []
  (keys @global-connections))

(defn test-db-name [db-spec]
  (keyword (:subprotocol db-spec)))

(def *db* nil)

;;;; Helpers

(defn first-non-runtime-cause [e]
  (if (isa? (type e) RuntimeException)
    (if-let [c (.getCause e)]
      (first-non-runtime-cause c)
      e)
    e))

(defmacro ignore-unsupported [& body]
  `(try (let [r# (do ~@body)] (if (coll? r#) (doall r#) r#))
        (catch Exception e#
          (if (isa? (type (first-non-runtime-cause e#))
                    UnsupportedOperationException)
            nil
            (throw e#)))))

(defmacro def-db-test [name & body]
  `(do ~@(for [db-spec available-specs]
           (let [db (test-db-name db-spec)]
             `(deftest ~(symbol (str name "-" (:subprotocol db-spec)))
                (when ((set (available-global-cnx)) ~db)
                  (binding [*db* ~db]
                    ~@body)))))))

(defmacro with-schema [[var-name sname] & body]
  `(try
     (let [~var-name (create-schema ~sname *db*)]
       ~@body)
     (finally (try (drop-schema ~sname :cascade *db*)
                   (catch Exception _#)))))

(defn get-schema []
  (get-global-schema :lobos *db*))

(defmacro are-equal [actual expected & [msg]]
  `(let [r# (ignore-unsupported ~actual)]
     (when r# (is (= r# ~expected) ~msg))
     r#))

(defmacro test-with [[var form] & body]
  `(let [~var (ignore-unsupported ~form)]
     (when ~var ~@body)))

;;;; Fixtures

(def tmp-files-ext '(db sqlite3))

(defn remove-tmp-files []
  (let [current-dir (file-seq (file "."))
        p (str ".*\\." (apply join "|" tmp-files-ext))
        tmp? #(re-find (re-pattern p) (str %))]
    (doseq [tmp-file (filter tmp? current-dir)]
      (delete-file tmp-file true))))

(defn remove-tmp-files-fixture [f]
  (remove-tmp-files)
  (f)
  (remove-tmp-files))

(defn open-global-connections-fixture [f]
  (doseq [db-spec db-specs]
    (when-not (driver-available? db-spec)
      (println (format "WARNING: Driver for %s isn't available: %s missing"
                       (:subprotocol db-spec)
                       (:classname db-spec)))))
  (doseq [db-spec available-specs]
    (try
      (open-global (test-db-name db-spec) db-spec)
      (catch Exception _
        (println "WARNING: Failed to connect to" (:subprotocol db-spec)))))
  (f)
  (doseq [db (available-global-cnx)]
    (close-global db)))
