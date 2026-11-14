-- Amplía las cuentas existentes sin sustituir sus identidades ni contraseñas.
ALTER TABLE app_user ADD COLUMN birth_date DATE;
ALTER TABLE app_user ADD COLUMN region VARCHAR(100);
ALTER TABLE app_user ADD COLUMN commune VARCHAR(100);
ALTER TABLE app_user ADD COLUMN address VARCHAR(300);
