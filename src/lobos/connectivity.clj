;; Copyright (c) Nicolas Buduroi. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 which can be found in the file
;; epl-v10.html at the root of this distribution. By using this software
;; in any fashion, you are agreeing to be bound by the terms of this
;; license.
;; You must not remove this notice, or any other, from this software.

(ns lobos.connectivity
  "A set of connectivity functions."
  (require (clojure.contrib.sql [internal :as sqlint]))
  (use (clojure.contrib [def :only [defalias defvar]])))

;;;; Global connection

(defvar global-connections
  (atom {})
  "The map of global connections in an atom.")

(defn open-global
  "Supplied with a keyword identifying a global connection, that connection
  is closed and the reference dropped."
  [db-spec & [connection-name]]
  (let [connection-name (or connection-name :default-connection)]
    (if-let [cnx (connection-name @global-connections)]
      (throw
       (Exception.
        (format "A global connection by that name already exists (%s)"
                connection-name)))
      (let [cnx (sqlint/get-connection db-spec)]
        (when-let [ac (-> db-spec :auto-commit)]
          (.setAutoCommit cnx ac))
        (swap! global-connections assoc
               (or connection-name :default-connection)
               {:connection cnx :db-spec db-spec})))))

(defn close-global
  "Supplied with a keyword identifying a global connection, that connection
  is closed and the reference dropped."
  [& [connection-name]]
  (let [connection-name (or connection-name :default-connection)
        cnx (connection-name @global-connections)]
    (if cnx
      (do
        (.close (:connection cnx))
        (swap! global-connections dissoc connection-name)
        true)
      (throw
       (Exception. (format "No global connection by that name is open: %s"
                           connection-name))))))

;;;; With connection

(defn with-named-connection
  "Evaluates func in the context of a named global connection to a
  database."
  [connection-name func]
  (io!
   (if-let [con (@global-connections connection-name)]
     (binding [sqlint/*db*
               (assoc sqlint/*db*
                 :connection (:connection con)
                 :level 0
                 :rollback (atom false)
                 :db-spec (:db-spec con))]
       (func))
     (throw
      (Exception.
       (format "No such global connection currently open: %s, only got %s"
               connection-name
               (vec (keys @global-connections))))))))

(defn with-spec-connection
  "Evaluates func in the context of a new connection to a database then
  closes the connection."
  [db-spec func]
  (with-open [con (sqlint/get-connection db-spec)]
    (binding [sqlint/*db* (assoc sqlint/*db*
                            :connection con
                            :level 0
                            :rollback (atom false)
                            :db-spec db-spec)]
      (.setAutoCommit con (or (:auto-commit db-spec) true))
      (func))))

(defmacro with-connection
  "Evaluates body in the context of a new connection or a named global
  connection to a database then closes the connection if it's a new
  one. The connection-info parameter can be a keyword denoting a global
  connection or a map containing values for one of the following
  parameter sets:

  Factory:
    :factory (required) a function of one argument, a map of params
    (others) (optional) passed to the factory function in a map

  DriverManager:
    :classname (required) a String, the jdbc driver class name
    :subprotocol (required) a String, the jdbc subprotocol
    :subname (required) a String, the jdbc subname
    (others) (optional) passed to the driver as properties.

  DataSource:
    :datasource (required) a javax.sql.DataSource
    :username (optional) a String
    :password (optional) a String, required if :username is supplied

  JNDI:
    :name (required) a String or javax.naming.Name
    :environment (optional) a java.util.Map

  Options (for ClojureQL):
    :auto-commit (optional) a Boolean
    :fetch-size  (optional) an integer"
  [connection-info & body]
  `(let [connection-info# (or ~connection-info :default-connection)]
     ((if (keyword? connection-info#)
        with-named-connection
        with-spec-connection) connection-info# (fn [] ~@body))))

;;;; Helpers

(defalias connection sqlint/connection*)

(defn default-connection
  "Returns the default connection if it exists."
  []
  (try
    (with-named-connection :default-connection
      connection)
    (catch Exception e nil)))

(defn get-db-spec
  "Returns the associated db-spec or itself."
  [& [connection-info]]
  (let [connection-info (or connection-info :default-connection)]
    (if (keyword? connection-info)
      (-> @global-connections connection-info :db-spec)
      connection-info)))
