(ns rezipeas.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [clojure.java.jdbc :refer [with-db-transaction]]
            [rezipeas.sql :refer :all]
            [rezipeas.pages :refer :all]
            [rezipeas.sanitize :refer :all]
            [ring.util.response :refer :all]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

(db-setup)

(defn save-new-ingredients [tx ingredients]
  """Inserts or ignores ingredients into database."""
  (doseq [ing ingredients]
      (insert-ingredient tx {:name ing})))

(defn save-new-tags [tx tags]
  """Inserts or ignores tags into database."""
  (doseq [tag tags]
    (insert-tag tx {:name tag})))

(defn save-rec-ing-relations [tx rec_id ingredients quantities units portions]
  """Inserts or ignores recipe-ingredient relation into database.
     Needs recipe id."""
  (doseq [row (map vector ingredients quantities units)]
    (let [ing_id (:id (first (get-ing-id tx {:name (first row)})))
          quantity (/ (second row) portions)
          unit (last row)]
      (insert-rec-ing
       tx
       {:rec_id rec_id, :ing_id ing_id
        ,:quantity quantity, :unit unit}))))

(defn save-tag-rec-relations [tx rec_id tags]
  """Inserts or ignors recipe-tag relation into database.
     Needs recipe id from database."""
  (doseq [tag tags]
    (let [tag_id (:id (first (get-tag-id tx {:name tag})))]
      (insert-tag-rec tx {:tag_id tag_id, :rec_id rec_id}))))


(defn save-new-recipe [params]
  """Commits a new recipe to the database."""
  (let [name (:name params)
        ingredients (sanitize-ingredients (:ingredient params))
        quantities (sanitize-quantities (:quantity params))
        units (sanitize-units (:unit params))
        intro (:intro params)
        tip (let [tip (:tip params)] (if tip tip ""))
        description (:description params)
        portions (sanitize-portions (:portions params))
        tags (sanitize-tags (:tag params))
        file? (not (empty? (get-in params [:picture :filename])))
        tempfile (get-in params [:picture :tempfile])
        filename (if file? 
                   (str name (get-file-extension (get-in params [:picture :filename])))
                   nil)]
    (when file?
      (io/copy tempfile (io/file (str rootpath "img" (java.io.File/separator) filename))))
    (with-db-transaction [tx db]
      (insert-recipe tx {:name name, :intro intro, :description description, :tip tip :portions portions :image_url filename})
      (save-new-ingredients tx ingredients)
      (save-new-tags tx tags)
      ;; need to cast portions to double, because hugsql does not convert ratios to reals
      (let [rec_id (:id (first (get-rec-id tx {:name name})))]
        (save-rec-ing-relations tx rec_id ingredients quantities units (double portions))
        (save-tag-rec-relations tx rec_id tags)))
    (let [rec_id (:id (first (get-rec-id db {:name name})))]
      (redirect (str "/recipies/" rec_id)))))

(defn save-edit-recipe [id params]
  """Updates database entries for recipe with given id and params."""
  (let [name (:name params)
        ingredients (sanitize-ingredients (:ingredient params))
        quantities (sanitize-quantities (:quantity params))
        units (sanitize-units (:unit params))
        intro (:intro params)
        tip (let [tip (:tip params)] (if tip tip ""))
        description (:description params)
        portions (sanitize-portions (:portions params))
        tags (sanitize-tags (:tag params))
        file? (not (empty? (get-in params [:picture :filename])))
        tempfile (get-in params [:picture :tempfile])
        filename (if file? 
                   (str name (get-file-extension (get-in params [:picture :filename])))
                   nil)]
    (when file?
      (io/copy tempfile
               (io/file
                (str
                 rootpath
                 "img"
                 (java.io.File/separator)
                 filename))))
    (with-db-transaction [tx db]
      (change-recipe tx (assoc params :id id :image_url filename))
      (save-new-ingredients tx ingredients)
      (save-new-tags tx tags)
      (delete-tagrec tx {:rec_id id})
      (delete-recing tx {:rec_id id})
      (save-rec-ing-relations tx id ingredients quantities units (double portions))
      (save-tag-rec-relations tx id tags)
      (delete-orphan-tags tx)
      (delete-orphan-ings tx))
    (redirect (str "/recipies/" id))))

(defn delete-recipe [id]
  """Removes the recipe with given id from database and performs
     cleanup operations."""
  (do
    (with-db-transaction [tx db]
      (delete-rec-by-id tx {:id id})
      (delete-tagrec tx {:rec_id id})
      (delete-recing tx {:rec_id id})
      (delete-orphan-tags tx)
      (delete-orphan-ings tx))
    (redirect "/")))

(defn rename-tag [id name]
  """Renames the given tag, if the name is not yet in the database."""
  (do
    (with-db-transaction [tx db]
      (rename-tag-with-id tx {:id id :name name}))
    (redirect "/tags")))
    
      
(defroutes app-routes
  (GET "/" [] (redirect "/recipies/new"))
  (GET "/search" [term tags] (show-search-result term tags))
  (GET "/recipies" [] (show-all-recipies))
  (GET "/recipies/new" [] (new-recipe))
  (GET "/recipies/:id" [id] (show-recipe id))
  (GET "/recipies/edit/:id" [id] (edit-recipe-page id))
  (GET "/recipies/delete/:id" [id] (show-delete-recipe id))
  (GET "/tags" [] (show-all-tags))
  (GET "/tags/:id" [id] (show-edit-tag id))
  (GET "/ingredients" [] (show-all-ingredients))
  (POST "/recipies/delete/:id" [id] (delete-recipe id))
  (POST "/recipies/edit/:id" [id & params] (save-edit-recipe id params))
  (POST "/recipies/new" req (save-new-recipe (:params req)))
  (POST "/tags/:id/rename" [id & params] (rename-tag id (:new-name params)))
  (route/files "/img/" {:root (str rootpath "img" (java.io.File/separator))})
  (route/resources "/assets/")
  (route/not-found "Not Found"))

(def app
  ;; enable anti-forgery later
  (wrap-defaults app-routes
                 (-> site-defaults
                     (assoc-in [:security :anti-forgery] false))))
