# KYNEX Backend

API REST de KYNEX para la evaluación EP3. Implementa cuentas, catálogo, carrito privado y pedidos con pago emulado persistidos en PostgreSQL y conectados con React. El proyecto completo continúa en desarrollo.

## Funciones disponibles

- Consulta pública de seis productos iniciales, imágenes y categorías.
- Alta, edición y baja lógica de productos y categorías con autorización de Administrador.
- Consulta pública de categorías por id. El bloqueo compartido entre asignación de productos y baja de categoría preserva las referencias activas ante concurrencia.
- Nombres de categoría únicos con plegado Unicode y normalización NFC, incluso con mayúsculas acentuadas y configuración regional distinta del servidor.
- Precios decimales, stock no negativo, códigos únicos, referencias y control de versión para evitar sobrescribir ediciones concurrentes.
- Registro de Cliente con RUN verificado, contraseña elegida y hash BCrypt. Conserva nombre, apellidos, correo, fecha de nacimiento opcional, región, comuna y dirección; comprueba su relación. El registro no admite asignar roles.
- Login, JWT de 10 minutos, identidad actual, renovación rotativa de siete días y logout. El cierre revoca también los accesos de la misma sesión.
- Renovación en cookie HttpOnly, verificador SHA-256 en la base y detección de reutilización. CSRF obligatorio para las operaciones de autenticación; CORS limitado a orígenes configurados.
- Rol y estado de cuenta consultados en la base en cada petición autenticada.
- CRUD administrativo de cuentas con contraseña inicial elegida, edición de rol y perfil, baja lógica y reactivación. No se devuelve el hash ni se cambia una contraseña al editar.
- Perfil personal limitado a la identidad autenticada. Versiones y coordinación en PostgreSQL evitan sobrescrituras y cambios administrativos concurrentes inconsistentes.
- Se impide desactivar la cuenta propia o cambiar su rol. La desactivación revoca las sesiones; reactivar no rehabilita tokens anteriores.
- Carrito por cuenta, conciliación idempotente del visitante y advertencias de disponibilidad. Versiones evitan sobrescribir cambios de otra pestaña.
- Pedidos con destinatario y productos históricos; precios y despacho calculados en el servidor. Crear un pedido no reserva ni descuenta inventario.
- Pago académico con aprobación, rechazo, cancelación, expiración y error técnico. Aprobación e inventario se actualizan en una transacción; repetir no duplica sus efectos.
- Historial propio; consulta administrativa para Administrador y Vendedor. Solo Administrador modifica estados; una anulación autorizada restituye stock una vez y registra reversión de prueba.
- Esquema versionado con Flyway, Swagger y comprobación de salud.

Ejercicios, rutinas y contacto se incorporarán en los siguientes bloques. Los comprobantes son internos del proyecto y no constituyen documentos tributarios.

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
$secretBytes = New-Object byte[] 48
$secretGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$secretGenerator.GetBytes($secretBytes)
[Convert]::ToBase64String($secretBytes)
$secretGenerator.Dispose()
```

Con Docker Desktop iniciado, crear la base:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
. .\scripts\Load-Environment.ps1
docker compose up -d postgres
.\mvnw.cmd spring-boot:run
```

Si PostgreSQL ya utiliza el puerto 5432 del equipo, cambiar estas dos líneas en `.env`:

```env
DB_PORT=5433
DB_URL=jdbc:postgresql://localhost:5433/kynex
```

Volver a cargar `.env` antes de ejecutar Docker y Maven, siguiendo el orden anterior. Así ambos servicios utilizan el mismo puerto y el backend no se conecta por error a otra instalación de PostgreSQL. El contenedor mantiene su puerto interno 5432 y sus datos persistentes.

También se admite PostgreSQL instalado directamente: crear una base vacía `kynex`, un usuario con permisos sobre ella y ajustar `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`. Si se cambia `DB_PORT` en Docker, también debe cambiarse el puerto de `DB_URL`.

El backend escucha en el puerto 8080. [Swagger local](http://localhost:8080/swagger-ui.html), [catálogo local](http://localhost:8080/api/v1/products), [salud local](http://localhost:8080/actuator/health).

Spring Boot recibe las variables del proceso; no lee `.env` automáticamente. El comando que carga el archivo debe ejecutarse en la misma ventana donde se inicia Maven. La política indicada permite ejecutar el cargador únicamente en esa ventana de PowerShell. No publicar `.env`, volcados de bases ni claves.

## Primera cuenta de administración

Antes del primer inicio, definir `BOOTSTRAP_ADMIN_EMAIL` y `BOOTSTRAP_ADMIN_PASSWORD` en `.env`. La contraseña debe tener al menos diez caracteres y como máximo 72 bytes UTF-8. La cuenta se crea solo si ese correo no existe. Quitar ambas variables después del alta y recargar el entorno. El inicio posterior no modifica cuentas existentes ni eleva un Cliente registrado.

No se incluyen cuentas con contraseñas predeterminadas. Después del alta inicial, Administrador puede crear Vendedores, Clientes u otros Administradores desde React, eligiendo su contraseña inicial y entregándola al titular por un canal privado.

## Contrato inicial

| Método | Ruta | Acceso |
| --- | --- | --- |
| GET | `/api/v1/products`, `/api/v1/products/{id}` | Público |
| GET | `/api/v1/categories`, `/api/v1/categories/{id}` | Público; solo activas |
| POST | `/api/v1/products`, `/api/v1/categories` | Administrador |
| PUT | `/api/v1/products/{id}`, `/api/v1/categories/{id}` | Administrador; enviar `version` |
| DELETE | `/api/v1/products/{id}?version=0`, `/api/v1/categories/{id}?version=0` | Administrador; baja lógica |
| GET | `/api/v1/auth/csrf` | Público; obtener token y cookie |
| POST | `/api/v1/auth/register`, `/api/v1/auth/login` | Público con CSRF |
| POST | `/api/v1/auth/refresh`, `/api/v1/auth/logout` | Cookie de renovación y CSRF |
| GET | `/api/v1/auth/me` | JWT válido |
| GET, POST | `/api/v1/admin/users` | Administrador; listado o creación |
| GET, PUT | `/api/v1/admin/users/{id}` | Administrador; consulta o edición con versión |
| DELETE | `/api/v1/admin/users/{id}?version=0` | Administrador; desactivación conservando historial |
| PUT | `/api/v1/admin/users/{id}/activation` | Administrador; reactivación con versión |
| GET, PUT | `/api/v1/profile` | Cuenta propietaria; edición con versión y sin rol ni estado |
| GET | `/api/v1/cart` | Cuenta propietaria |
| PUT | `/api/v1/cart/items/{id}` | Propietario; `quantity` y `version`; cero retira la línea |
| DELETE | `/api/v1/cart?version=0` | Cuenta propietaria |
| POST | `/api/v1/cart/merge` | Propietario; UUID e `items` con id y cantidad |
| POST | `/api/v1/orders` | Propietario; UUID, versión del carrito y `delivery` |
| GET | `/api/v1/orders`, `/api/v1/orders/{id}` | Solo pedidos propios |
| GET | `/api/v1/payment-options` | Cuenta autenticada; disponibilidad y escenarios |
| POST | `/api/v1/orders/{id}/payments` | Propietario; UUID y escenario de prueba |
| POST | `/api/v1/orders/{id}/payments/{paymentId}/confirm` | Propietario; cuerpo vacío, resultado decidido por el servidor |
| GET | `/api/v1/admin/orders`, `/api/v1/admin/orders/{id}` | Administrador o Vendedor |
| PUT | `/api/v1/admin/orders/{id}/status` | Administrador; estado, versión y motivo |

Antes de una operación de autenticación, consultar `/auth/csrf` conservando las cookies y enviar su `token` en la cabecera `X-XSRF-TOKEN`. Swagger incluye esta cabecera en las operaciones correspondientes. El registro exige `run`, `firstName`, `lastName`, `email`, `password`, `region`, `commune` y `address`; `birthDate` es opcional y debe ser anterior a hoy. Las ubicaciones admitidas coinciden con el catálogo de los formularios KYNEX. Las operaciones administrativas utilizan `Authorization: Bearer <accessToken>`.

El producto utiliza `categoryId`, `code`, `name`, `description`, `price`, `stock`, `criticalStock`, `image`, `featured` y `details`. `details` conserva presentación, ingredientes, nutrición y recomendaciones del catálogo. La respuesta aplana esos metadatos para mantener el contrato de los componentes React. Las imágenes iniciales se sirven en `/media/products/`.

La categoría contiene `id`, `name` y `version`. El nombre se recorta y admite hasta 80 caracteres. La migración V7 utiliza el soporte Unicode incorporado de PostgreSQL 18 con base UTF8 para que `PROTEÍNAS` y `Proteínas`, incluidas formas equivalentes del acento, no creen nombres distintos. Se conserva el texto de presentación. Si hubiera duplicados históricos, la creación del índice rechaza la migración en una transacción; no se borran ni fusionan datos automáticamente. La configuración utilizada se describe en [la documentación de PostgreSQL 18](https://www.postgresql.org/docs/18/collation.html).

Una categoría con productos activos no puede desactivarse. Crear o reasignar un producto y desactivar su categoría comparten bloqueo en PostgreSQL: una de las operaciones se completa y la otra reevalúa el estado resultante. La baja conserva la categoría y sus referencias históricas, pero la retira de las consultas públicas. Las ediciones y bajas requieren la versión vigente.

Los errores de validación devuelven 400 y campos en `errors`. Las credenciales inválidas devuelven 401; los permisos insuficientes, 403; los recursos inexistentes, 404; los duplicados, referencias activas y conflictos de versión, 409.

## Gestión de cuentas y perfil

La creación recibe `{ profile, role }`: `profile` utiliza los campos del registro y la contraseña elegida; `role` admite `ADMINISTRADOR`, `VENDEDOR` o `CLIENTE`. La edición recibe `{ profile, role, version }` y excluye la contraseña. Las respuestas presentan el rol en español, `active` y `version`. El listado incluye cuentas inactivas para poder reactivarlas.

El perfil personal recibe `{ profile, version }`; no admite id de propietario, rol, estado ni contraseña. El servidor valida RUN y correo únicos, ubicación, fecha y límites. Su identidad se obtiene del JWT. Los cambios conservan las copias históricas de los pedidos. El Administrador inicial debe completar su RUN y dirección antes de guardar su perfil.

Los cambios de administración se serializan con una fila de coordinación y vuelven a comprobar el rol del actor. El servidor protege la cuenta propia y la existencia de administración activa. Las operaciones de renovación y desactivación bloquean primero la cuenta y después las sesiones para evitar ciclos de bloqueo. Una baja conserva los registros y revoca sus familias de sesión; después de reactivar se requiere un nuevo login. El rol vigente se aplica también a JWT ya emitidos.

## Compra y pago académico

Definir `PAYMENT_EMULATOR_ENABLED=true` en `.env` y recargar el entorno antes de iniciar Maven. Por defecto el servidor deshabilita el emulador si no recibe esta opción. No realiza cobros, no solicita tarjetas y no contacta una pasarela externa.

`delivery` contiene `recipient`, `region`, `commune` y `address`. El servidor valida la ubicación y obtiene precios desde el catálogo. El despacho cuesta $3.990 bajo $50.000 y es gratuito desde ese monto; un carrito vacío suma cero. El pedido pendiente vence a los 15 minutos y cada intento, a los cinco minutos como máximo. Los escenarios son `APPROVE`, `REJECT`, `CANCEL`, `EXPIRE` y `TECHNICAL_ERROR`. Cada intención lleva una clave UUID; reutilizarla con datos distintos produce conflicto.

El pago aprobado verifica el stock actual, descuenta todas las líneas de manera conjunta, confirma el pedido y retira solo las cantidades compradas del mismo carrito. El historial conserva precios y nombres originales. Los pagos negativos conservan stock y carrito. Dos compras simultáneas no venden la misma última unidad. Una cuenta no puede leer ni pagar pedidos ajenos.

Los estados avanzan de `CONFIRMED` a `PREPARING`, `SHIPPED` y `DELIVERED`. Administrador puede anular desde `PENDING`, `CONFIRMED` o `PREPARING`; no desde despacho o entrega. La anulación pagada devuelve inventario una sola vez y marca una reversión académica. El motivo y las fechas quedan registrados. La expiración se evalúa al consultar u operar el pedido; no requiere un proceso programado.

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

Las pruebas rechazan una base cuyo nombre no termine en `_test` antes de aplicar migraciones. Los casos transaccionales revierten sus cambios; los casos concurrentes utilizan registros propios y los limpian. La prueba de stock restaura el producto utilizado. Las 89 pruebas comprueban controladores, propiedad, idempotencia, expiración, roles, versiones, bajas, unicidad Unicode y concurrencia sobre PostgreSQL real. GitHub Actions dispone de su propia base aislada.

Para obtener el ejecutable:

```powershell
.\mvnw.cmd package
java -jar target\kynex-backend-0.1.0-SNAPSHOT.jar
```

La segunda orden utiliza las mismas variables del entorno. El esquema se valida al iniciar y Flyway aplica únicamente las migraciones pendientes; los datos iniciales no reemplazan las ediciones guardadas.

## Conectar React

En la rama EP3 del frontend, copiar `.env.example` como `.env`, definir `VITE_DATA_SOURCE=api` y usar `VITE_API_URL=http://localhost:8080/api/v1`. Reiniciar Vite después de cambiar estas variables. El origen de Vite debe aparecer en `CORS_ALLOWED_ORIGINS`; utilizar `localhost` en ambos servicios para las cookies de sesión. La opción anterior `VITE_CATALOG_SOURCE=api` conserva compatibilidad y ahora conecta también las cuentas.

Esta activación conecta catálogo, cuentas, gestión de productos, carrito, checkout, pagos y pedidos. React mantiene el JWT en memoria y recupera la sesión mediante la cookie HttpOnly; no utiliza identidades ni contraseñas demostrativas. Las pestañas coordinan las mutaciones de sesión con Web Locks y reciben avisos sin credenciales mediante BroadcastChannel. Se requiere un navegador moderno y HTTPS en despliegue; localhost admite las pruebas HTTP.

El Administrador gestiona usuarios, categorías, productos y estados de pedidos; el Vendedor conserva consulta de productos y pedidos. Cada cuenta dispone de Mi perfil. La interfaz conserva metadatos y versión al editar y actualiza el catálogo tras renombrar una categoría. Rutinas y contacto mantienen su alcance local hasta su integración.

## Condiciones de operación

`COOKIE_SECURE=false` se admite solo en desarrollo HTTP local. En despliegue se necesita HTTPS, cookies seguras, orígenes explícitos, credenciales externas, limitación de intentos, copias de seguridad y prueba de restauración. Esta versión no se presenta como lista para operación productiva. El emulador no efectúa cobros reales ni solicita datos de tarjetas.
