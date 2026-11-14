# KYNEX Backend

API REST de KYNEX para la evaluación EP3. Implementa catálogo y autenticación persistidos en PostgreSQL, conectados con los formularios de cuenta y la gestión de productos de React. El proyecto completo continúa en desarrollo.

## Funciones disponibles

- Consulta pública de seis productos iniciales, imágenes y categorías.
- Alta, edición y baja lógica de productos y categorías con autorización de Administrador.
- Precios decimales, stock no negativo, códigos únicos, referencias y control de versión para evitar sobrescribir ediciones concurrentes.
- Registro de Cliente con RUN verificado, contraseña elegida y hash BCrypt. Conserva nombre, apellidos, correo, fecha de nacimiento opcional, región, comuna y dirección; comprueba su relación. El registro no admite asignar roles.
- Login, JWT de 10 minutos, identidad actual, renovación rotativa de siete días y logout. El cierre revoca también los accesos de la misma sesión.
- Renovación en cookie HttpOnly, verificador SHA-256 en la base y detección de reutilización. CSRF obligatorio para las operaciones de autenticación; CORS limitado a orígenes configurados.
- Rol y estado de cuenta consultados en la base en cada petición autenticada.
- Esquema versionado con Flyway, Swagger y comprobación de salud.

Carrito por cuenta, boletas, pagos emulados, perfil, gestión completa de usuarios, ejercicios, rutinas y contacto se incorporarán en los siguientes bloques. No existen endpoints ficticios que confirmen estas operaciones.

## Requisitos

Java 21, PostgreSQL 18 y Git. El lanzador incluido descarga Maven 3.9.16 la primera vez; requiere conexión a Internet. Se utiliza Spring Boot 3.5.16 y springdoc 2.10.0.

## Ejecutar en Windows PowerShell

```powershell
git clone https://github.com/AlexanderLlanten/kynex-backend.git
cd kynex-backend
Copy-Item .env.example .env
```

Editar `.env`: elegir `DB_PASSWORD` y reemplazar `JWT_SECRET` por un valor aleatorio. Este comando genera un secreto para copiarlo al archivo:

```powershell
[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
```

Con Docker Desktop iniciado, crear la base:

```powershell
docker compose up -d postgres
. .\scripts\Load-Environment.ps1
.\mvnw.cmd spring-boot:run
```

También se admite PostgreSQL instalado directamente: crear una base vacía `kynex`, un usuario con permisos sobre ella y ajustar `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`. Si se cambia `DB_PORT` en Docker, también debe cambiarse el puerto de `DB_URL`.

El backend escucha en el puerto 8080. [Swagger local](http://localhost:8080/swagger-ui.html), [catálogo local](http://localhost:8080/api/v1/products), [salud local](http://localhost:8080/actuator/health).

Spring Boot recibe las variables del proceso; no lee `.env` automáticamente. El comando que carga el archivo debe ejecutarse en la misma ventana donde se inicia Maven. No publicar `.env`, volcados de bases ni claves.

## Primera cuenta de administración

Antes del primer inicio, definir `BOOTSTRAP_ADMIN_EMAIL` y `BOOTSTRAP_ADMIN_PASSWORD` en `.env`. La contraseña debe tener al menos diez caracteres y como máximo 72 bytes UTF-8. La cuenta se crea solo si ese correo no existe. Quitar ambas variables después del alta y recargar el entorno. El inicio posterior no modifica cuentas existentes ni eleva un Cliente registrado.

No se incluyen cuentas con contraseñas predeterminadas. El usuario Vendedor todavía no tiene interfaz de alta; sus permisos están comprobados mediante pruebas de API.

## Contrato inicial

| Método | Ruta | Acceso |
| --- | --- | --- |
| GET | `/api/v1/products`, `/api/v1/products/{id}` | Público |
| GET | `/api/v1/categories` | Público |
| POST | `/api/v1/products`, `/api/v1/categories` | Administrador |
| PUT | `/api/v1/products/{id}`, `/api/v1/categories/{id}` | Administrador; enviar `version` |
| DELETE | `/api/v1/products/{id}?version=0`, `/api/v1/categories/{id}?version=0` | Administrador; baja lógica |
| GET | `/api/v1/auth/csrf` | Público; obtener token y cookie |
| POST | `/api/v1/auth/register`, `/api/v1/auth/login` | Público con CSRF |
| POST | `/api/v1/auth/refresh`, `/api/v1/auth/logout` | Cookie de renovación y CSRF |
| GET | `/api/v1/auth/me` | JWT válido |

Antes de una operación de autenticación, consultar `/auth/csrf` conservando las cookies y enviar su `token` en la cabecera `X-XSRF-TOKEN`. Swagger incluye esta cabecera en las operaciones correspondientes. El registro exige `run`, `firstName`, `lastName`, `email`, `password`, `region`, `commune` y `address`; `birthDate` es opcional y debe ser anterior a hoy. Las ubicaciones admitidas coinciden con el catálogo de los formularios KYNEX. Las operaciones administrativas utilizan `Authorization: Bearer <accessToken>`.

El producto utiliza `categoryId`, `code`, `name`, `description`, `price`, `stock`, `criticalStock`, `image`, `featured` y `details`. `details` conserva presentación, ingredientes, nutrición y recomendaciones del catálogo. La respuesta aplana esos metadatos para mantener el contrato de los componentes React. Las imágenes iniciales se sirven en `/media/products/`.

Los errores de validación devuelven 400 y campos en `errors`. Las credenciales inválidas devuelven 401; los permisos insuficientes, 403; los recursos inexistentes, 404; los duplicados, referencias activas y conflictos de versión, 409.

## Pruebas con PostgreSQL

Crear otra base vacía llamada `kynex_test`, con el mismo usuario, antes de ejecutar:

```powershell
. .\scripts\Load-Environment.ps1
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/kynex_test'
.\mvnw.cmd verify
```

Con Docker puede crearse en el mismo contenedor:

```powershell
docker compose exec postgres sh -c 'createdb -U "$POSTGRES_USER" kynex_test'
```

Las pruebas rechazan una base cuyo nombre no termine en `_test` antes de aplicar migraciones. Cada caso revierte sus cambios. Se comprueban controladores, seguridad y JPA sobre PostgreSQL real; no se sustituye por H2. GitHub Actions dispone de su propia base aislada.

Para obtener el ejecutable:

```powershell
.\mvnw.cmd package
java -jar target\kynex-backend-0.1.0-SNAPSHOT.jar
```

La segunda orden utiliza las mismas variables del entorno. El esquema se valida al iniciar y Flyway aplica únicamente las migraciones pendientes; los datos iniciales no reemplazan las ediciones guardadas.

## Conectar React

En la rama EP3 del frontend, copiar `.env.example` como `.env`, definir `VITE_DATA_SOURCE=api` y usar `VITE_API_URL=http://localhost:8080/api/v1`. Reiniciar Vite después de cambiar estas variables. El origen de Vite debe aparecer en `CORS_ALLOWED_ORIGINS`; utilizar `localhost` en ambos servicios para las cookies de sesión. La opción anterior `VITE_CATALOG_SOURCE=api` conserva compatibilidad y ahora conecta también las cuentas.

Esta activación conecta catálogo, registro, login, recuperación de identidad, renovación, logout y gestión de productos. React mantiene el JWT en memoria y recupera la sesión mediante la cookie HttpOnly; no utiliza identidades ni contraseñas demostrativas. Las pestañas coordinan las mutaciones de sesión con Web Locks y reciben avisos sin credenciales mediante BroadcastChannel. Se requiere un navegador moderno y HTTPS en despliegue; localhost admite las pruebas HTTP.

El Administrador crea, edita y desactiva productos desde React; el Vendedor conserva consulta. La interfaz obtiene las categorías vigentes y conserva metadatos y versión al editar. Usuarios y pedidos administrativos muestran disponibilidad futura mientras se integran, evitando presentar registros demostrativos como datos del servidor. Carrito por cuenta, rutinas y compra siguen pendientes.

## Condiciones de operación

`COOKIE_SECURE=false` se admite solo en desarrollo HTTP local. En despliegue se necesita HTTPS, cookies seguras, orígenes explícitos, credenciales externas, limitación de intentos, copias de seguridad y prueba de restauración. Esta versión inicial no se presenta como lista para operación productiva. La pasarela académica futura no efectuará cobros reales ni solicitará datos de tarjetas.
