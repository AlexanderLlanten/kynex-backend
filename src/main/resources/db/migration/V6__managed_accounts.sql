ALTER TABLE app_user ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);

-- Un bloqueo común serializa los cambios de administración, incluso entre procesos distintos.
CREATE TABLE account_policy (
    id INTEGER PRIMARY KEY CHECK (id = 1)
);
INSERT INTO account_policy (id) VALUES (1);
