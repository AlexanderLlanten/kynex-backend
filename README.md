# KYNEX Backend

API REST de KYNEX para la evaluación EP3. Implementa cuentas, catálogo, carrito privado y pedidos con pago emulado persistidos en PostgreSQL y conectados con React. El proyecto completo continúa en desarrollo.

## Arquitectura por módulos de negocio

El backend es un **monolito modular**: una aplicación Spring Boot, un ejecutable y una base PostgreSQL. Los paquetes agrupan cada función del negocio y mantienen sus propias capas:

```text
cl.kynex
├── KynexApplication
├── identity       Usuarios, autenticación, roles y sesiones
├── catalog        Productos, categorías e inventario
├── commerce       Carrito, pedidos y pagos emulados
├── training       Ejercicios y rutinas privadas
├── contact        Consultas, atención y respuestas
├── shared         Errores comunes y catálogo de ubicaciones
└── config         Documentación OpenAPI general
```

Cada módulo de negocio contiene `controller`, `service`, `model`, `repository` y `dto`. `controller` recibe HTTP; `service` coordina reglas y transacciones; `model` representa entidades y estados; `repository` accede a PostgreSQL; `dto` define las entradas y respuestas. La configuración de seguridad está en `identity/config` y la de compra en `commerce/config`. Los contratos usados entre módulos están en `identity/api` y `catalog/api`; son interfaces Java internas y no agregan endpoints HTTP.

Identidad expone `AccountAccess` para comprobar cuentas, coordinar administración y obtener datos permitidos sin entregar entidades ni contraseñas. Contacto y entrenamiento utilizan ese contrato y no dependen del carrito. Comercio accede al catálogo mediante `InventoryAccess`; los repositorios de cada módulo solo son utilizados dentro de ese módulo. Ambos contratos requieren la transacción del consumidor para mantener los bloqueos hasta finalizar la operación.

Las dependencias permitidas son: identidad y catálogo hacia utilidades compartidas; contacto y entrenamiento hacia la API interna de identidad; comercio hacia identidad y el catálogo. Se conserva expresamente la relación JPA entre la línea del carrito y el producto, y el acceso al modelo de inventario dentro de la transacción comercial. Aprobar o anular un pago mantiene juntos pedido, carrito e inventario. La extracción a servicios independientes requerirá sustituir esa coordinación y separar la propiedad de los datos; organizar paquetes no produce despliegues independientes.

Diez pruebas con ArchUnit verifican límites de paquetes, dependencias permitidas, ausencia de ciclos, repositorios internos, separación de capas y participación en la transacción del consumidor. Se ejecutan sin una base de datos:

```powershell
.\mvnw.cmd "-Dtest=ModuleArchitectureTest" test
```

Las pruebas funcionales permanecen organizadas en `identity`, `commerce`, `training`, `contact` e `integration`; el soporte común está en `support`. La regresión completa conserva PostgreSQL real y añade las pruebas de arquitectura: **168 pruebas aprobadas**. Las rutas `/api/v1`, los contratos HTTP, las tablas, las migraciones y los recursos estáticos se mantienen.

Después de descargar esta reorganización se debe detener la instancia anterior y compilar con `clean` para retirar clases generadas que conservan los paquetes antiguos. La limpieza afecta la salida de Maven; no elimina la base ni `.env`.

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
- Biblioteca pública de ocho ejercicios con fotografías originales. Administrador puede crear, editar, desactivar y reactivar ejercicios con control de versión.
- Rutinas privadas múltiples por cuenta, nombre único por propietario, prescripciones personales y confirmación de versión. La baja de ejercicios conserva las referencias existentes.

- Recepción pública de contacto con referencia idempotente, validación en servidor y confirmación posterior a la persistencia.
- Bandeja de contacto exclusiva de Administrador, filtro de estado, páginas de 20 registros, notas internas, historial y control de cambios simultáneos.
- Consultas vinculadas a cada cuenta y respuestas publicadas por Administrador, separadas de las notas internas. Recuperación de consultas anteriores mediante referencia y correo coincidente.

Los comprobantes son internos del proyecto y no constituyen documentos tributarios.

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
.\mvnw.cmd clean spring-boot:run
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

Si el editor Java y Maven compilan simultáneamente y aparecen archivos de clase ausentes, puede utilizarse una salida independiente, ya excluida del repositorio:

```powershell
.\mvnw.cmd -Dkynex.build.directory=.local/maven clean spring-boot:run
```

Esta opción conserva `target` como salida predeterminada y permite que la ejecución de Maven use `.local/maven`. El comando debe iniciarse con el backend anterior detenido y las variables de entorno cargadas.

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
| GET | `/api/v1/exercises`, `/api/v1/exercises/{id}` | Público; ejercicios activos |
| GET, POST | `/api/v1/admin/exercises` | Administrador; listado completo o alta |
| GET, PUT, DELETE | `/api/v1/admin/exercises/{id}` | Administrador; consulta, edición o baja lógica; escrituras con versión |
| PUT | `/api/v1/admin/exercises/{id}/activation` | Administrador; reactivación con versión |
| GET, POST | `/api/v1/routines` | Rutinas propias; listado o creación |
| GET, PUT, DELETE | `/api/v1/routines/{id}` | Propietario; consulta, renombre o eliminación con versión |
| POST, PUT, DELETE | `/api/v1/routines/{id}/items/{exerciseId}` | Propietario; agregar, ajustar o retirar con versión |
| DELETE | `/api/v1/routines/{id}/items?version=0` | Propietario; vaciar ejercicios conservando la rutina |
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

## Ejercicios y rutinas

V8 incorpora una vez los ocho ejercicios y sus fotografías en `/media/training/`. El ejercicio incluye nombre, descripción, grupo muscular, dificultad, equipamiento, imagen, icono y series/repeticiones sugeridas. Los nombres son únicos con normalización Unicode. Administrador conserva control de versión al editar, desactivar o reactivar; los ejercicios inactivos quedan fuera de la biblioteca pública.

Crear una rutina recibe `{ name }`; renombrarla recibe `{ name, version }`. El nombre admite hasta 100 caracteres y es único dentro de cada cuenta. Se admiten hasta 20 rutinas por propietario y 100 ejercicios distintos por rutina. Agregar recibe `{ version }` y utiliza las sugerencias del servidor; ajustar recibe `{ sets, reps, version }`, con enteros entre 1–20 y 1–100 respectivamente. El navegador no puede elegir el propietario. Los accesos ajenos devuelven 404 y no modifican datos.

Las escrituras se serializan con el bloqueo de la cuenta y comprueban la versión de la rutina. El cambio concurrente se rechaza antes de sobrescribir. Eliminar una rutina retira únicamente sus ítems; vaciar conserva el nombre. Desactivar un ejercicio no elimina sus referencias previas y editar sus sugerencias no altera la prescripción personal. La cuenta desactivada conserva sus rutinas para una reactivación posterior.

## Contacto y atención interna

V10 incorpora propietario y respuestas publicadas sin convertir las notas existentes en respuestas. Un envío autenticado utiliza `POST /api/v1/my/contact` con `{ submissionKey, comment }`: el servidor obtiene propietario, nombre y correo de la cuenta. No admite elegir otra identidad. Las consultas propias se recuperan mediante `GET /api/v1/my/contact?page=0` y `GET /api/v1/my/contact/{id}`; las respuestas excluyen notas, historial interno e identidad del administrador. Un identificador ajeno devuelve 404, incluso si el lector es Administrador.

Las consultas antiguas y las enviadas sin sesión no se asignan automáticamente por correo. `POST /api/v1/my/contact/claims` recibe `{ reference }` y exige sesión, la referencia UUID de recepción y el mismo correo en la cuenta. El vínculo no puede transferirse. Una vez vinculado, la propiedad sigue a la cuenta aunque cambie su correo.

Administrador puede recuperar el histórico con `POST /api/v1/admin/contact/{id}/account-link` y `{ version }`, después de comprobar al remitente. El servidor selecciona la cuenta activa del mismo correo; no admite enviar otro propietario. El vínculo conserva consulta, notas y respuestas y registra al administrador en el historial. Repetirlo no transfiere la propiedad ni duplica el evento. No se concede acceso a contactos ajenos por coincidencia de correo durante el login o registro.

Administrador publica mediante `POST /api/v1/admin/contact/{id}/replies` con `{ text, replyKey, version }`. La respuesta admite 2000 caracteres y se conserva en su propia tabla; la clave UUID evita duplicar publicaciones al perder una respuesta de red. Una consulta pendiente pasa a En atención. Los conflictos de versión devuelven 409. Las notas anteriores permanecen privadas: para contestar al remitente debe escribirse expresamente una respuesta publicada. Su lectura se realiza dentro de la cuenta y no envía correo.

V9 añade `contact_message` y `contact_event` sin sustituir datos existentes. `POST /api/v1/contact` es público y recibe `{ submissionKey, name, email, comment }`. La referencia es un UUID generado por el cliente; repetir la misma referencia y contenido devuelve el mismo recibo sin duplicar el registro ni reiniciar su atención. Reutilizarla con contenido diferente devuelve 409. La respuesta pública contiene únicamente `reference` y `receivedAt`.

El nombre y correo admiten 100 caracteres y el comentario 500. Se recortan espacios exteriores y se normaliza el correo. Se conservan los dominios `duoc.cl`, `profesor.duoc.cl` y `gmail.com`. No se admite elegir id, propietario, estado o notas en el envío público. No existe una consulta pública del contenido recibido.

| Método | Ruta | Acceso y resultado |
| --- | --- | --- |
| POST | `/api/v1/contact` | Público; recibo tras persistir, 201 |
| GET | `/api/v1/admin/contact?status=PENDIENTE&page=0` | Administrador; filtro opcional y páginas de 20 registros |
| GET | `/api/v1/admin/contact/{id}` | Administrador; contenido original, atención e historial |
| PUT | `/api/v1/admin/contact/{id}` | Administrador; `{ status, note, version }` |

Los estados son `PENDIENTE`, `EN_ATENCION` y `RESUELTA`. Resolver exige una nota de atención; la nota admite 1000 caracteres. Reabrir conserva el historial. Cada cambio registra fecha, estado, nota e identidad del administrador en el servidor. El mensaje original no se edita. Los cambios usan versión y bloqueo transaccional; un conflicto devuelve 409. Las notas son internas y no se envían por correo. Cliente y Vendedor reciben 403 en toda la gestión. Swagger identifica el envío público y las operaciones protegidas.

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

Las pruebas rechazan una base cuyo nombre no termine en `_test` antes de aplicar migraciones. Los casos transaccionales revierten sus cambios; los casos concurrentes utilizan registros propios y los limpian. La prueba de stock restaura el producto utilizado. Las 158 pruebas funcionales comprueban controladores, propiedad, límites, idempotencia, expiración, roles, versiones, bajas, Unicode y concurrencia sobre PostgreSQL real; otras diez verifican la arquitectura, para un total de 168. Contacto incluye recuperación administrativa del histórico, respuestas por propietario, separación de notas privadas, reintentos concurrentes, atención e historial, cambios de rol y contratos Swagger. GitHub Actions dispone de su propia base aislada.

Para obtener el ejecutable:

```powershell
.\mvnw.cmd clean package
java -jar target\kynex-backend-0.1.0-SNAPSHOT.jar
```

La segunda orden utiliza las mismas variables del entorno. El esquema se valida al iniciar y Flyway aplica únicamente las migraciones pendientes; los datos iniciales no reemplazan las ediciones guardadas.

## Conectar React

En la rama EP3 del frontend, copiar `.env.example` como `.env`, definir `VITE_DATA_SOURCE=api` y usar `VITE_API_URL=http://localhost:8080/api/v1`. Reiniciar Vite después de cambiar estas variables. El origen de Vite debe aparecer en `CORS_ALLOWED_ORIGINS`; utilizar `localhost` en ambos servicios para las cookies de sesión. La opción anterior `VITE_CATALOG_SOURCE=api` conserva compatibilidad y ahora conecta también las cuentas.

Esta activación conecta catálogo, cuentas, gestión de productos, carrito, checkout, pagos y pedidos. React mantiene el JWT en memoria y recupera la sesión mediante la cookie HttpOnly; no utiliza identidades ni contraseñas demostrativas. Las pestañas coordinan las mutaciones de sesión con Web Locks y reciben avisos sin credenciales mediante BroadcastChannel. Se requiere un navegador moderno y HTTPS en despliegue; localhost admite las pruebas HTTP.

El Administrador gestiona usuarios, categorías, productos, ejercicios, estados de pedidos y atención de contacto; el Vendedor conserva consulta administrativa de productos y pedidos. Cada cuenta dispone de Mi perfil y rutinas propias. La interfaz conserva metadatos y versión al editar. Contacto confirma la recepción persistida y su atención utiliza una bandeja protegida.

## Condiciones de operación

### Copia de seguridad y recuperación

Con Docker Desktop abierto y PostgreSQL iniciado, PowerShell 5.1 o superior puede crear una copia sin detener la aplicación:

```powershell
$copia = .\scripts\Backup-Database.ps1
$copia.BackupPath
```

La salida predeterminada es `.local/backups`. Se deben conservar juntos el archivo `.dump` y su `.dump.json`, que registra fecha UTC, versión de PostgreSQL, tamaño y SHA-256. Estas copias contienen información privada y quedan excluidas de Git. Deben guardarse también fuera del equipo, en una ubicación con acceso restringido. La comprobación SHA-256 detecta alteraciones respecto del manifiesto; no constituye una firma de autenticidad. Solo deben restaurarse copias de procedencia confiable.

La recuperación crea otra base y exige un nuevo ingreso de las cuentas:

```powershell
$recuperacion = .\scripts\Restore-Database.ps1 -BackupPath $copia.BackupPath
$recuperacion.Database
```

El destino generado comienza por `kynex_restore_`. Se rechazan bases existentes y el origen; no se borran ni sobrescriben. La restauración utiliza una transacción y verifica el archivo antes de crear el destino. Las sesiones recuperadas quedan revocadas, mientras que las originales permanecen intactas. Un fallo conserva únicamente la base nueva para su diagnóstico.

Antes de utilizar la base recuperada se deben verificar sus datos. Para seleccionarla, cambiar **solo el nombre final de la base** en `DB_URL` dentro de `.env`, conservando el servidor y puerto propios; detener el backend que se está utilizando, cargar nuevamente el entorno e iniciarlo. El original se conserva hasta confirmar la recuperación. No se deben iniciar dos backend en el mismo puerto. La salud puede consultarse en `http://localhost:8080/actuator/health`.

Las herramientas trabajan con el servicio `postgres` de este Compose. Admiten `-ProjectName` y `-EnvironmentFile` para seleccionar explícitamente otro proyecto. La copia admite `-Database` y `-OutputDirectory`; la recuperación admite `-Database` únicamente para un nombre nuevo con el prefijo requerido. El archivo no incluye `.env`, secretos, roles globales de PostgreSQL ni recursos estáticos del repositorio: deben conservarse por separado. No es una herramienta de migración entre versiones distintas de PostgreSQL. El formato se basa en [pg_dump](https://www.postgresql.org/docs/18/app-pgdump.html) y [pg_restore](https://www.postgresql.org/docs/18/app-pgrestore.html) de PostgreSQL 18.

La comprobación operativa crea y retira únicamente bases de prueba con nombres aleatorios:

```powershell
.\tests\DatabaseRecovery.Tests.ps1
```

Comprueba 11 escenarios de integridad, Unicode, importes, claves foráneas, secuencias, sesiones y rechazo de sobrescrituras o archivos alterados. Se verificó en PowerShell 5.1 y 7; GitHub Actions la ejecuta en un proyecto Docker independiente. No requiere ejecutar nuevamente la regresión de aplicación para un cambio exclusivo de estas herramientas.

`COOKIE_SECURE=false` se admite solo en desarrollo HTTP local. En despliegue se necesita HTTPS, cookies seguras, orígenes explícitos, credenciales externas, limitación de intentos, copias de seguridad y prueba de restauración. Esta versión no se presenta como lista para operación productiva. El emulador no efectúa cobros reales ni solicita datos de tarjetas.
