(ns rezipeas.sql
  (:require [hugsql.core :as hug])
  (:gen-class))

(def rootpath (str (. System getProperty "user.dir") (java.io.File/separator)))

(def dbname "kochbuch.db")

(def db {:dbtype "sqlite"
         :dbname (str rootpath "kochbuch.db")})

(hug/def-db-fns "db-setup.sql")
(hug/def-db-fns "selects.sql")
(hug/def-db-fns "inserts.sql")

(defn db-setup []
  """Creates all tables, if not existent."""
  (do
    (create-recipe-table db)
    (create-ingredient-table db)
    (create-tag-table db)
    (create-rec-ingredients-table db)
    (create-tag-rec-table db)))
