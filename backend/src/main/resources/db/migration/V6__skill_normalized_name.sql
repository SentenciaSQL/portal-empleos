ALTER TABLE skills ADD COLUMN normalized_name VARCHAR(80);

UPDATE skills
SET normalized_name = lower(regexp_replace(btrim(name), '\s+', ' ', 'g'))
WHERE normalized_name IS NULL;

ALTER TABLE skills ALTER COLUMN normalized_name SET NOT NULL;

ALTER TABLE skills ADD CONSTRAINT uq_skills_normalized_name UNIQUE (normalized_name);
