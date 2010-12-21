;; Copyright (c) Nicolas Buduroi. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 which can be found in the file
;; epl-v10.html at the root of this distribution. By using this software
;; in any fashion, you are agreeing to be bound by the terms of this
;; license.
;; You must not remove this notice, or any other, from this software.

(ns lobos.schema
  "The abstract schema data-structure and some function to help creating
  one."
  (:refer-clojure :exclude [drop])
  (require (lobos [ast :as ast])))

;;;; Protocols

(defprotocol Buildable
  (build [this]))

(defprotocol Creatable
  (create [this cnx]))

(defprotocol Dropable
  (drop [this behavior cnx]))

;;;; Constraint definition

(defrecord Constraint [cname ctype specification]
  Buildable
  
  (build [this]
    (condp contains? ctype
      #{:unique :primary-key}
      (lobos.ast.UniqueConstraintDefinition.
       cname
       ctype
       (:columns specification)))))

(defn constraint
  "Constructs an abstract constraint definition and add it to the given
  table."
  [table constraint-name constraint-type specification]
  (update-in table [:constraints] conj
             (lobos.schema.Constraint. constraint-name
                                       constraint-type
                                       specification)))

(defn unique-constraint
  "Constructs an abstract unique (or primary-key depending on the given
  type) constraint definition and add it to the given table."
  [table constraint-type name-or-column columns]
  (let [named (contains? (-> table :columns set) name-or-column)
        constraint-name (when named name-or-column)
        columns (if named
                  columns
                  (conj columns name-or-column))]
    (constraint table
                constraint-name
                constraint-type
                {:columns (vec columns)})))

(defn primary-key
  "Constructs an abstract primary key constraint definition and add it
  to the given table."
  [table name-or-column & columns]
  (unique-constraint table :primary-key name-or-column columns))

(defn unique
  "Constructs an abstract unique constraint definition and add it to the
  given table."
  [table name-or-column & columns]
  (unique-constraint table :unique name-or-column columns))

;;;; Column definition

(defrecord DataType [dtype args])

(defrecord Column [cname data-type default identity not-null others]
  Buildable
  
  (build [this]
    (lobos.ast.ColumnDefinition.
     cname
     (lobos.ast.DataTypeExpression.
      (:dtype data-type)
      (:args data-type))
     (when default (lobos.ast.ValueExpression. default))
     identity
     not-null
     others)))

(defn column
  "Constructs an abstract column definition and add it to the given
  table."
  [table column-name data-type options]
  (let [default nil
        default  (first (filter vector? options))
        others   (vec (filter string? options))
        options  (set options)
        not-null (boolean (options :not-null))
        identity (boolean (options :identity))]
    (#(cond (options :primary-key) (primary-key % column-name)
            (options :unique) (unique % column-name)
            :else %)
     (update-in table [:columns] conj
                (lobos.schema.Column. column-name
                                      data-type
                                      (second default)
                                      identity
                                      not-null
                                      others)))))

(defn integer
  "Constructs an abstract integer column definition and add it to the
  given table."
  [table column-name & options]
  (let [dtype (lobos.schema.DataType. :integer nil)]
    (column table column-name dtype options)))

(defn varchar
  "Constructs an abstract varchar column definition and add it to the
  given table."
  [table column-name & [limit & options]]
  (let [limit (when (integer? limit) limit)
        dtype (lobos.schema.DataType. :varchar [limit])
        options (if limit
                  options
                  (conj options limit))]
    (column table column-name dtype options)))

(defn timestamp
  "Constructs an abstract timestamp column definition and add it to the
  given table."
  [table column-name & options]
  (let [dtype (lobos.schema.DataType. :timestamp nil)]
    (column table column-name dtype options)))

;;;; Table definition

(defrecord Table [tname columns constraints options]
  Creatable Dropable
  
  (create [this cnx]
    (lobos.ast.CreateTableStatement.
     cnx
     tname
     (map build (concat columns constraints))))

  (drop [this behavior cnx]
    (lobos.ast.DropStatement. cnx :table tname behavior)))

(defn table*
  "Constructs an abstract table definition."
  [table-name columns constraints options]
  (lobos.schema.Table. table-name columns constraints options))

(defmacro table
  "Constructs an abstract table definition containing the given
  elements."
  [name & elements]
  `(-> (table* ~name [] [] {}) ~@elements))

;;;; Schema definition

(defrecord Schema [sname elements options]
  Creatable Dropable
  
  (create [this cnx]
    (lobos.ast.CreateSchemaStatement.
     cnx
     sname
     (map #(create % cnx) elements)))

  (drop [this behavior cnx]
    (lobos.ast.DropStatement. cnx :schema sname behavior)))

(defn schema
  "Constructs an abstract schema definition."
  [schema-name options & elements]
  (lobos.schema.Schema. schema-name (vec elements) (or options {})))

(defmacro defschema
  "Defines a var containing the specified schema."
  {:arglists '([var-name schema-name? options? & elements])}
  [& args]
  (let [[var-name & args] args
        [farg sarg & rargs] args
        sname (when (keyword? farg) farg)
        options (if sname
                  (when (map? sarg) sarg)
                  (when (map? farg) farg))
        elements (cond (and sname options) rargs
                       (or sname options) (next args)
                       :else args)
        sname (or sname 'default_schema)]
    `(def ~var-name
          (schema ~(keyword sname) ~options ~@elements))))
