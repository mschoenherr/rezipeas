-- :name get-rec-by-name
-- :command :query
-- :doc Retrieve a recipe by name
SELECT * FROM recipies WHERE instr(name,:name)>0;

-- :name get-recs
-- :command :query
-- :doc Get all recipies, ordered by name
SELECT * FROM recipies ORDER BY name;

-- :name get-tags
-- :command :query
-- :doc Get list of all tags
SELECT * FROM tags ORDER BY name;

-- :name get-ingredients
-- :command :query
-- :doc Get list of all ingredients
SELECT * FROM ingredients ORDER BY name;

-- :name get-rec-ingredients
-- :command :query
-- :doc Get list of all ingredients for a particular recipe
SELECT DISTINCT ingredients.id, ingredients.name, recing.quantity, recing.unit
FROM ingredients JOIN recing
ON ingredients.id = recing.ing_id
WHERE recing.rec_id = :rec_id;

-- write some query to get recipe by full-text search and tags