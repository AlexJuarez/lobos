;; Copyright (c) Nicolas Buduroi. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 which can be found in the file
;; epl-v10.html at the root of this distribution. By using this software
;; in any fashion, you are agreeing to be bound by the terms of this
;; license.
;; You must not remove this notice, or any other, from this software.

(ns lobos.analyzer
  "Analyze a database's meta-data to contruct an abstract schema."
  (:refer-clojure :exclude [replace])
  (:require (lobos [connectivity :as conn]
                   [schema :as schema]))
  (:use (clojure.contrib [def :only [defvar defvar-]])
        (clojure [string :only [lower-case
                                replace
                                upper-case]])
        lobos.utils)
  (:import (lobos.schema Column
                         Constraint
                         DataType)))

;;;; Helpers

(defn as-keyword [s]
  (-> s lower-case (replace \_ \-) keyword))

;;;; Metadata

(defvar *db-meta* nil)

(defn db-meta
  "Returns the binded DatabaseMetaData object found in *db-meta* or get
  one from the default connection if not available."
  []
  (or *db-meta*
      (conn/with-connection :default-connection
        (.getMetaData (conn/connection)))))

(defmacro with-db-meta
  "Evaluates body in the context of a new connection or a named global
  connection to a database then closes the connection while binding its
  DatabaseMetaData object to *db-meta*."
  [db-spec & body]
  `(if *db-meta*
     (do ~@body)
     (conn/with-connection ~db-spec
       (binding [*db-meta* (.getMetaData (conn/connection))]
         ~@body))))

;;;; Constraints analysis

(defn indexes
  "Returns all indexes for the specified schema and table names, results
  are sorted by ordinal position, grouped by index name and can be
  filtered by the given function f."
  [sname tname & [f]]
  (group-by :index_name
    (sort-by :ordinal_position
      (filter (or f identity)
        (resultset-seq (.getIndexInfo (db-meta) nil
                                      (name sname)
                                      (name tname)
                                      false
                                      false))))))

(defn primary-keys
  "Returns primary key names as a set of keywords."
  [sname tname]
  (set
   (map #(-> % :pk_name keyword)
        (resultset-seq (.getPrimaryKeys (db-meta) nil
                                        (name sname)
                                        (name tname))))))

(defn unique-constraints
  "Returns a list of unique constraints for the specified schema and
  table names."
  [sname tname]
  (let [pkeys (primary-keys sname tname)]
    (map (fn [[cname cmeta]]
           (Constraint. (keyword cname)
                        (if (pkeys (keyword cname))
                          :primary-key
                          :unique)
                        {:columns
                         (vec (map #(-> % :column_name keyword)
                                   cmeta))}))
         (indexes sname tname #(-> % :non_unique not)))))

(defn constraints
  "Returns a list of constraints for the specified schema and table
  names. Supports only unique constraints at the moment."
  [sname tname]
  (unique-constraints sname tname))

;;;; Columns analysis

(defvar- dtypes-aliases
  {:bool   :boolean
   :bytea  :blob
   :float4 :real
   :float8 :float
   :int2   :smallint
   :int4   :integer
   :int8   :bigint
   :text   :clob})

(defn analyze-data-type
  "Returns an abstract data-type definition given a column meta-data."
  [col-meta]
  (let [dtype (-> col-meta :type_name as-keyword)
        dtype (dtypes-replace dtypes-aliases dtype)]
    (DataType.
     dtype
     (case dtype
       :varchar [(:column_size col-meta)]
       []))))

(defn analyze-expression
  "Returns the Clojure equivalent of the given SQL expression. Just a
  stub currently."
  [expr]
  (when expr
    (if-let [[[_ func]] (re-seq #"(\w+)\(\)" expr)]
      (keyword func)
      (let [[_ & [value dtype]] (first (re-seq #"(.*)::(.*)" expr))]
        (read-string (replace (str value) \' \"))))))

(defn analyze-column
  "Returns an abstract column definition given a column meta-data."
  [col-meta]
  (let [auto-inc (= (:is_autoincrement col-meta) "YES")]
    (Column. (-> col-meta :column_name keyword)
             (analyze-data-type col-meta)
             (when-not auto-inc
               (analyze-expression (:column_def col-meta)))
             auto-inc
             (= (:is_nullable col-meta) "NO")
             [])))

(defn columns
  "Returns a list of abstract column definitions for the specified
  schema and table names."
  [sname tname]
  (map analyze-column
       (resultset-seq (.getColumns (db-meta) nil
                                   (name sname)
                                   (name tname)
                                   nil))))

;;;; Tables analysis

(defn analyze-table
  "Returns the abstract table definition for the specified schema and
  table names."
  [sname tname]
  (schema/table* tname
                 (into {} (map #(vector (:cname %) %)
                               (columns sname tname)))
                 (into {} (map #(vector (:cname %) %)
                               (constraints sname tname)))
                 {}))

(defn tables
  "Returns a list of abstract table definitions for the specified schema name."
  [sname]
  (map #(analyze-table sname (-> % :table_name keyword))
       (resultset-seq (.getTables (db-meta) nil
                                  (name sname)
                                  nil
                                  (into-array ["TABLE"])))))

;;;; Schemas analysis

(defn schemas
  "Returns a list of schema names as keywords."
  []
  (map #(-> % :table_schem keyword)
       (doall (resultset-seq (.getSchemas (db-meta))))))

(defn analyze-schema
  "Returns the abstract schema definition for the specified schema name
  using the given connection-info if specified or the default one."
  [sname & [connection-info]]
  (let [sname (keyword sname)
        db-spec (conn/get-db-spec connection-info)
        options {:db-spec db-spec}]
    (with-db-meta connection-info
      (when ((set (schemas)) sname)
        (apply schema/schema sname options (tables sname))))))
