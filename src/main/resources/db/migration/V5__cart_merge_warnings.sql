ALTER TABLE cart_merge ADD COLUMN warnings JSONB NOT NULL DEFAULT '[]'::jsonb;
