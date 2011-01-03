;; Copyright (c) Nicolas Buduroi. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 which can be found in the file
;; epl-v10.html at the root of this distribution. By using this software
;; in any fashion, you are agreeing to be bound by the terms of this
;; license.
;; You must not remove this notice, or any other, from this software.

(ns lobos.backends.mysql
  "Compiler implementation for MySQL."
  (:refer-clojure :exclude [compile])
  (:use (clojure [string :only [join]])
        lobos.compiler)
  (:import (lobos.ast AutoIncClause
                      CreateSchemaStatement
                      CreateTableStatement
                      DropStatement
                      Identifier)))

(defmethod compile [:mysql Identifier]
  [identifier]
  (let [{:keys [value]} identifier]
    (as-str \` value \`)))

(defmethod compile [:mysql AutoIncClause]
  [_]
  "AUTO_INCREMENT")

(defmethod compile [:mysql CreateSchemaStatement]
  [statement]
  (let [{:keys [db-spec sname elements]} statement]
    (join ";\n\n"
          (conj (map (comp compile
                           #(assoc-in % [:db-spec :schema] sname))
                     elements)
                (str "CREATE SCHEMA IF NOT EXISTS "
                     (as-identifier db-spec sname))))))

(defmethod compile [:mysql DropStatement]
  [statement]
  (let [{:keys [db-spec otype oname behavior]} statement]
    (join \space
      (concat
       ["DROP"
        (as-sql-keyword otype)
        (as-schema-qualified-identifier db-spec oname)]
       (when (and behavior (#{:table} otype))
         [(as-sql-keyword behavior)])))))
