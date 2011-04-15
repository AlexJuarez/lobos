;; Copyright (c) Nicolas Buduroi. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 which can be found in the file
;; epl-v10.html at the root of this distribution. By using this software
;; in any fashion, you are agreeing to be bound by the terms of this
;; license.
;; You must not remove this notice, or any other, from this software.

(ns lobos.test.system
  (:refer-clojure :exclude [compile conj! disj! distinct drop sort take])
  (:require (clojure.contrib [sql :as sql])
            (lobos [compiler :as compiler]
                   [connectivity :as conn]))
  (:use clojure.test
        (lobos [core :only [create-schema drop-schema]] test)
        (lobos.backends h2 mysql postgresql sqlite sqlserver)
        lobos.test.sample-schema))

;;;; Fixtures

(defn create-schemas-for-all-db []
  (doseq [db (available-global-cnx)]
    (create-schema sample-schema db)))

(defn drop-schemas-for-all-db []
  (doseq [db (available-global-cnx)]
    (try (drop-schema sample-schema :cascade db)
         (catch Exception _))))

(defn use-sample-schema-fixture [f]
  (try (create-schemas-for-all-db)
       (f)
       (finally (drop-schemas-for-all-db))))

(use-fixtures :once
  remove-tmp-files-fixture
  open-global-connections-fixture
  use-sample-schema-fixture)

;;;; Helpers

(defn table [name]
  (compiler/as-identifier
   (assoc (conn/get-db-spec *db*) :schema :lobos)
   name
   :schema))

(defn identifier [name]
  (compiler/as-identifier (conn/get-db-spec *db*) name))

;;;; Tests

(def-db-test test-check-constraint
  (when-not (= *db* :mysql)
    (sql/with-connection (conn/get-db-spec *db*)
      (is (thrown? Exception
                   (sql/insert-records (table :users)
                                       {(identifier :name) "x"}))
          "An exception should have been thrown because of a check constraint")
      (is (nil? (sql/insert-records (table :users)
                                    {(identifier :name) "foo"}))
          "A new record should have been inserted into the users table"))))

(def-db-test test-unique-constraint
  (sql/with-connection (conn/get-db-spec *db*)
    (is (thrown? Exception
                 (sql/insert-records (table :users)
                                     {(identifier :name) "foo"}))
        "An exception should have been thrown because of an unique constraint")
    (is (nil? (sql/insert-records (table :users)
                                  {(identifier :name) "bar"}))
        "A new record should have been inserted into the users table")))

(def-db-test test-foreign-key-constraint
  (sql/with-connection (conn/get-db-spec *db*)
    (is (thrown? Exception
                 (sql/insert-records (table :posts)
                                     {(identifier :title) "foo"
                                      (identifier :user_id) 1}))
        "An exception should have been thrown because of a foreign key constraint")
    (is (nil? (sql/insert-records (table :posts)
                                  {(identifier :title) "foo"
                                   (identifier :user_id) 2}))
        "A new record should have been inserted into the posts table")))
