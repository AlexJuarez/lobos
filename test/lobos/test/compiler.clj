;; Copyright (c) Nicolas Buduroi. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 which can be found in the file
;; epl-v10.html at the root of this distribution. By using this software
;; in any fashion, you are agreeing to be bound by the terms of this
;; license.
;; You must not remove this notice, or any other, from this software.

(ns lobos.test.compiler
  (:refer-clojure :exclude [compile])
  (:use [lobos.compiler] :reload)
  (:use [clojure.test]))

(deftest test-compile-value-expression
  (is (= (compile (lobos.ast.ValueExpression. nil :foo))
         "FOO()")
      "Compiling a parameter-less function")
  (is (= (compile (lobos.ast.ValueExpression. nil "foo"))
         "'foo'")
      "Compiling a string value"))

(deftest test-compile-auto-inc-clause
  (is (= (compile (lobos.ast.AutoIncClause. nil))
         "GENERATED ALWAYS AS IDENTITY")
      "Compiling an auto-incrementing clause"))

(def column-definition-stub
     (lobos.ast.ColumnDefinition.
      nil
      :foo
      (lobos.ast.DataTypeExpression. nil :integer nil)
      nil
      false
      false
      []))

(deftest test-compile-column-definition
  (is (= (compile column-definition-stub)
         "foo INTEGER")
      "Compiling a simple column definition")
  (is (= (compile (assoc column-definition-stub
                    :default (lobos.ast.ValueExpression. nil 0)))
         "foo INTEGER DEFAULT 0")
      "Compiling a column definition with default value")
  (is (= (compile (assoc column-definition-stub
                    :auto-inc (lobos.ast.AutoIncClause. nil)))
         "foo INTEGER GENERATED ALWAYS AS IDENTITY")
      "Compiling a column definition with auto-incrementing clause")
  (is (= (compile (assoc column-definition-stub
                    :not-null true))
         "foo INTEGER NOT NULL")
      "Compiling a column definition with not null option")
  (is (= (compile (assoc column-definition-stub
                    :others ["BAR" "BAZ"]))
         "foo INTEGER BAR BAZ")
      "Compiling a column definition with custom options"))

(def unique-constraint-definition-stub
     (lobos.ast.UniqueConstraintDefinition.
      nil
      nil
      :unique
      [:foo :bar :baz]))

(deftest test-compile-unique-constraint-definition
  (is (= (compile unique-constraint-definition-stub)
         "UNIQUE (foo, bar, baz)")
      "Compiling an unnamed unique constraint definition")
  (is (= (compile (assoc unique-constraint-definition-stub
                    :cname :foo))
         "foo UNIQUE (foo, bar, baz)")
      "Compiling a named unique constraint definition")
  (is (= (compile (assoc unique-constraint-definition-stub
                    :ctype :primary-key))
         "PRIMARY KEY (foo, bar, baz)")
      "Compiling an unnamed primary key constraint definition"))

(def create-schema-statement-stub
     (lobos.ast.CreateSchemaStatement.
      nil
      :foo
      []))

(def create-table-statement-stub
     (lobos.ast.CreateTableStatement.
      nil
      :foo
      []))

(deftest test-compile-create-schema-statement
  (is (= (compile create-schema-statement-stub)
         "CREATE SCHEMA foo")
      "Compiling an empty create schema statement")
  (is (= (compile (assoc create-schema-statement-stub
                    :elements [create-table-statement-stub]))
         "CREATE SCHEMA foo\n\nCREATE TABLE foo ()")
      "Compiling a create schema statement containing a table"))

(deftest test-compile-create-table-statement
  (is (= (compile create-table-statement-stub)
         "CREATE TABLE foo ()")
      "Compiling a create table statement"))

(def drop-statement-stub
     (lobos.ast.DropStatement.
      nil
      :schema
      :foo
      nil))

(deftest test-compile-drop-statement
  (is (= (compile drop-statement-stub)
         "DROP SCHEMA foo")
      "Compiling a drop statement")
  (is (= (compile (assoc drop-statement-stub
                    :behavior :cascade))
         "DROP SCHEMA foo CASCADE")
      "Compiling a drop statement with cascade behavior"))
