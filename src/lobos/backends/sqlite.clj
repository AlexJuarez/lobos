;; Copyright (c) Nicolas Buduroi. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 which can be found in the file
;; epl-v10.html at the root of this distribution. By using this software
;; in any fashion, you are agreeing to be bound by the terms of this
;; license.
;; You must not remove this notice, or any other, from this software.

(ns lobos.backends.sqlite
  "Compiler implementation for SQLite."
  (:refer-clojure :exclude [compile])
  (:require (lobos [schema :as schema]))
  (:use (clojure.contrib [def :only [defvar-]])
        lobos.analyzer
        lobos.compiler
        lobos.metadata
        lobos.utils)
  (:import (lobos.ast AutoIncClause
                      CreateSchemaStatement
                      CreateTableStatement
                      DataTypeExpression
                      DropStatement
                      Identifier)
           (lobos.schema Column
                         DataType
                         UniqueConstraint
                         Table)))

;;;; Analyzer

(defvar- analyzer-data-type-aliases
  {:time-with-time-zone :time
   :timestamp-with-time-zone :timestamp})

(defmethod analyze [:sqlite DataType]
  [_ column-meta]
  (let [dtype (-> column-meta :type_name as-keyword)
        tz? #{:time-with-time-zone :timestamp-with-time-zone}
        [dtype options] (if (tz? dtype)
                          [dtype {:time-zone true}]
                          [dtype nil])
        dtype (first (replace analyzer-data-type-aliases [dtype]))
        args (analyze-data-type-args dtype column-meta)]
    (schema/data-type
     dtype
     (if (#{:decimal :numeric} dtype)
       [(first args)]
       args)
     options)))

(defmethod analyze [:sqlite UniqueConstraint]
  [_ sname tname cname index-meta]
  (let [columns (vec (map #(-> % :column_name keyword)
                          index-meta))]
  (UniqueConstraint.
   (make-constraint-name tname :unique columns)
   :unique
   columns)))

(defn analyze-primary-keys [tname]
  (let [columns (reduce
                 #(conj %1 (-> %2 :column_name keyword))
                 []
                 (resultset-seq
                  (.getPrimaryKeys (db-meta) nil nil (name tname))))]
    (when (not-empty columns)
      (UniqueConstraint.
       (make-constraint-name tname :primary-key columns)
       :primary-key
       columns))))

(defn sqlite-constraints [sname tname]
  (map (fn [[cname meta]] (analyze UniqueConstraint sname tname cname meta))
       (indexes-meta sname tname #(let [nu (:non_unique %)]
                                    (or (false? nu) (= nu 0))))))

(defmethod analyze [:sqlite Table]
  [_ sname tname]
  (let [pkey (analyze-primary-keys tname)]
    (schema/table* tname
                   (into {} (map #(let [c (analyze Column %)]
                                    [(:cname c) c])
                                 (columns-meta sname tname)))
                   (into {} (map #(vector (:cname %) %)
                                 (concat (sqlite-constraints sname tname)
                                         (when pkey [pkey])))))))

;;;; Compiler

(defmethod compile [:sqlite Identifier]
  [identifier]
  (as-str (:value identifier)))

(defmethod compile [:sqlite DataTypeExpression]
  [expression]
  (let [{:keys [dtype args options]} expression
        {:keys [collate time-zone]} options]
    (unsupported (and (#{:decimal :numeric} dtype) (= (count args) 2))
      "Doesn't support scale argument.")
    (join \space
      (str (as-sql-keyword dtype) (as-list args))
      (when collate (str "COLLATE " (as-str collate)))
      (when time-zone "WITH TIME ZONE"))))

(defmethod compile [:sqlite AutoIncClause]
  [_]
  nil)

(defmethod compile [:sqlite CreateSchemaStatement]
  [statement]
  (let [{:keys [db-spec sname elements]} statement]
    (apply join ";\n\n"
      (map (comp compile
                 #(assoc-in % [:db-spec :schema] sname))
           elements))))

(defmethod compile [:sqlite DropStatement]
  [statement]
  (let [{:keys [db-spec otype oname behavior]} statement]
    (when (#{:table} otype)
      (join \space
        "DROP"
        (as-sql-keyword otype)
        (as-identifier db-spec oname)))))
