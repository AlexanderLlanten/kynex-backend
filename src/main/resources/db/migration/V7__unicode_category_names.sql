-- La unicidad no depende del LC_CTYPE del sistema operativo; incluye mayúsculas acentuadas y formas equivalentes.
-- PostgreSQL 18 y base UTF8, como en la configuración del proyecto.
DROP INDEX category_name_idx;
CREATE UNIQUE INDEX category_name_idx ON category (
    normalize(casefold(normalize(name, NFC) COLLATE pg_catalog.pg_unicode_fast), NFC)
);
