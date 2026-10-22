# NEXO · Backend de gestión y seguridad

Plantilla REST con **Java 21, Spring Boot 4.1.1 y PostgreSQL**. Integra autenticación
JWT, un rol por usuario, permisos individuales y módulos de clientes, productos y
ventas. El frontend se encuentra en la carpeta hermana `Frontend` y utiliza
HTML, CSS y JavaScript.

## Contenido

- [Inicio rápido](#inicio-rápido)
- [Configuración por perfiles y JWT](#configuración-por-perfiles-y-jwt)
- [Administrador inicial y autenticación](#administrador-inicial-y-autenticación)
- [Modelo de roles y permisos](#modelo-de-roles-y-permisos)
- [Endpoints](#endpoints)
- [Flujo de usuarios y ventas](#flujo-de-usuarios-y-ventas)
- [Persistencia y estructura](#persistencia-y-estructura)
- [Frontend y Swagger](#frontend-y-swagger)
- [Pruebas y extensión](#pruebas-y-extensión)

## Inicio rápido

Requisitos: JDK 21 y PostgreSQL en ejecución. El proyecto incluye Gradle Wrapper
8.14.3; no necesitas instalar Gradle. En IntelliJ selecciona JDK 21 como Project
SDK y Gradle JVM.

Crea la base de datos desde PostgreSQL:

```sql
CREATE DATABASE sistema_gestion_usuarios;
```

Desde PowerShell:

```powershell
cd 'D:\Spring Boot Proyectos\sguridad java 21\spring-security-user-role-management'
$env:DB_URL = 'jdbc:postgresql://localhost:5432/sistema_gestion_usuarios'
$env:DB_USERNAME = 'postgres'
$env:DB_PASSWORD = '<contraseña de tu PostgreSQL>'
.\gradlew.bat bootRun
```

El perfil predeterminado es `dev` y el puerto es `8050`. Hibernate utiliza
`ddl-auto=update` para crear o actualizar las tablas dentro de una base existente.
El banner de arranque identifica el proyecto como NEXO y muestra Spring Boot,
Java y el perfil activo.

Para generar el ejecutable:

```powershell
.\gradlew.bat clean test bootJar
java -jar build/libs/usermanagement-0.0.1-SNAPSHOT.jar
```

Usa el nombre real del archivo generado en `build/libs` si difiere del ejemplo.

## Configuración por perfiles y JWT

| Archivo | Responsabilidad |
| --- | --- |
| [application.properties](src/main/resources/application.properties) | Selección del perfil predeterminado `dev` |
| [application-dev.properties](src/main/resources/application-dev.properties) | PostgreSQL, puerto, bootstrap y JWT de desarrollo |
| [application-test.properties](src/test/resources/application-test.properties) | H2 y configuración independiente para pruebas |
| `application-prod.properties` | Archivo que puedes crear para la configuración de producción |

Configuración JWT en **application-dev.properties**:

```properties
security.jwt.secret=${JWT_SECRET:User_M4n4gement}
security.jwt.issuer=${JWT_ISSUER:User_R0les_M4n4gement}
security.jwt.expiration=${JWT_EXPIRATION:15d}
```

HMAC256 usa una misma clave para firmar y verificar. La aplicación verifica firma,
emisor y vencimiento. `expiration` admite `30m`, `8h`, `15d` o ISO-8601, como
`PT30M`. Debe ser al menos un segundo; clave y emisor no pueden estar vacíos.
La configuración se valida al arrancar.

Puedes editar el archivo o usar estas variables:

| Variable | Uso |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexión PostgreSQL del perfil dev |
| `JWT_SECRET`, `JWT_ISSUER`, `JWT_EXPIRATION` | Firma, emisor y vigencia del token en dev |
| `ADMIN_USERNAME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Administrador inicial de dev |
| `BOOTSTRAP_ADMIN_ENABLED` | Activa/desactiva la creación de esa cuenta en dev |
| `SPRING_PROFILES_ACTIVE` | Selecciona el perfil que se ejecutará |

Por ejemplo:

```powershell
$env:JWT_EXPIRATION = '8h'
.\gradlew.bat bootRun
```

Reinicia el backend después de modificar propiedades. Cambiar la clave o el emisor
invalida los tokens anteriores. Cambiar la duración afecta solo a tokens nuevos:
los existentes conservan su vencimiento original.

Cuando crees `application-prod.properties`, define allí su conexión y sus propias
propiedades JWT. Puedes usar `security.jwt.secret=${JWT_SECRET}` para exigir una
clave suministrada por el entorno, sin el valor de desarrollo. Define también
`security.jwt.issuer` y `security.jwt.expiration`, y activa el perfil con:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'prod'
.\gradlew.bat bootRun
```

Actualmente no se incluye ese archivo. El perfil prod no hereda la configuración
de `application-dev.properties`; debes definir los valores necesarios. Para un
despliegue, utiliza una clave JWT propia, credenciales propias y orígenes CORS
acordes a tu frontend. La configuración actual permite cualquier origen.

## Administrador inicial y autenticación

Al iniciar se completan los catálogos base. ADMIN recibe los **20 permisos base**,
incluidos los comerciales. CUSTOMER recibe únicamente `random_order`. Los roles
personalizados y sus permisos se almacenan en la base de datos.

En dev se crea una cuenta inicial si no existe:

| Campo | Valor de desarrollo |
| --- | --- |
| Usuario | `superadmin` |
| Contraseña | `SuperAdmin123!` |
| Rol | `ADMIN` |

Las credenciales de la aplicación son independientes de las de PostgreSQL.
Las contraseñas se guardan con BCrypt y no aparecen en las respuestas.

Los reinicios no reemplazan contraseña, correo o estado de la cuenta inicial.
Cambiar `ADMIN_PASSWORD` después de creada no cambia su contraseña. Si el nombre
configurado ya pertenece a una cuenta sin ADMIN, el arranque falla para evitar
convertir esa cuenta en administrador automáticamente. `BOOTSTRAP_ADMIN_ENABLED=false`
desactiva la creación de la cuenta en dev, pero conserva la inicialización del
catálogo. Fuera de dev la creación está desactivada por defecto; puede configurarse
mediante `app.bootstrap.enabled`, `username`, `email` y `password`.

1. Ejecuta `POST /api/auth/login`:

```json
{"username":"superadmin","password":"SuperAdmin123!"}
```

2. Recibirás el JWT como **texto**, no como un objeto JSON.
3. Envía `Authorization: Bearer <token>` en las operaciones protegidas.
4. `GET /api/auth/me` devuelve tu rol, permisos heredados, individuales y efectivos.

El login valida contraseña y estado de cuenta. Cada petición con JWT vuelve a
cargar permisos y estado: bloquear, deshabilitar o eliminar una cuenta impide
seguir usando sus tokens. Cambiar permisos se refleja en la siguiente petición.
No hay endpoint de renovación ni lista de revocación de tokens; cambiar una
contraseña no invalida por sí solo los tokens que ya se emitieron.

## Modelo de roles y permisos

Cada cuenta tiene **un único rol**. Sus permisos efectivos son:

```text
permisos del rol + permisos individuales del usuario
```

Dos cuentas con CUSTOMER pueden tener accesos diferentes. Cambiar el rol conserva
los permisos individuales. Retirar una concesión individual no retira el mismo
permiso si también se hereda del rol. Los permisos protegen operaciones completas,
sin restricciones de propietario o empresa.

El catálogo es dinámico. Crear `REPORT_EXPORT` registra una autoridad, pero no crea
funcionalidades: el endpoint que la use debe verificarla, por ejemplo:

```java
@PreAuthorize("hasAuthority('REPORT_EXPORT')")
```

Los nombres del catálogo son sensibles a mayúsculas y admiten hasta 50 caracteres:
letras, números y guion bajo, comenzando con una letra. ADMIN no recibe
automáticamente permisos personalizados. Sus permisos base, y `random_order`
de CUSTOMER, están reservados por el bootstrap; retirarlos mediante la API
devuelve 409.

## Endpoints

### Autenticación y usuarios

| Método | Ruta | Permiso |
| --- | --- | --- |
| POST | `/api/auth/login` | Público |
| GET | `/api/auth/me` | Usuario autenticado |
| GET | `/api/user/all` | USER_READ |
| POST | `/api/user/add` | USER_CREATE; autorizaciones adicionales según el body |
| PUT | `/api/user/update` | USER_UPDATE; autorizaciones adicionales según el body |
| DELETE | `/api/user/delete/{name}` | USER_DELETE |
| POST | `/api/user/assignRole` | ROLE_ASSIGN |
| POST | `/api/user/assignPermission` | PERMISSION_ASSIGN |
| DELETE | `/api/user/{username}/permissions/{permission}` | PERMISSION_ASSIGN |
| GET | `/api/user/{username}/permissions` | USER_READ |
| GET | `/api/user/permissions` | PERMISSION_ASSIGN |

Crear con un rol explícito requiere ROLE_ASSIGN. Crear con permisos individuales
no vacíos requiere PERMISSION_ASSIGN. En PUT, enviar `role` o `roles` requiere
ROLE_ASSIGN; enviar `additionalPermissions`, incluso vacío, requiere
PERMISSION_ASSIGN.

### Catálogos de seguridad

| Método | Ruta | Permiso |
| --- | --- | --- |
| GET | `/api/roles` | ROLE_MANAGE |
| POST | `/api/roles` | ROLE_MANAGE; PERMISSION_MANAGE si incluye permisos no vacíos |
| GET | `/api/permissions` | PERMISSION_MANAGE |
| POST | `/api/permissions` | PERMISSION_MANAGE |
| PUT | `/api/roles/{role}/permissions/{permission}` | ROLE_MANAGE y PERMISSION_MANAGE |
| DELETE | `/api/roles/{role}/permissions/{permission}` | ROLE_MANAGE y PERMISSION_MANAGE |

Crear un rol con permisos es transaccional: todos deben existir. Repetir una
vinculación no la duplica. No hay endpoints de eliminación de roles o permisos.

### Clientes, productos y ventas

| Método | Ruta | Permiso |
| --- | --- | --- |
| GET | `/api/customers` y `/api/customers/{id}` | CUSTOMER_READ |
| POST | `/api/customers` | CUSTOMER_CREATE |
| PUT | `/api/customers/{id}` | CUSTOMER_UPDATE |
| DELETE | `/api/customers/{id}` | CUSTOMER_DELETE |
| GET | `/api/products` y `/api/products/{id}` | PRODUCT_READ |
| POST | `/api/products` | PRODUCT_CREATE |
| PUT | `/api/products/{id}` | PRODUCT_UPDATE |
| DELETE | `/api/products/{id}` | PRODUCT_DELETE |
| GET | `/api/sales` y `/api/sales/{id}` | SALE_READ |
| POST | `/api/sales` | SALE_CREATE |
| POST | `/api/sales/{id}/cancel` | SALE_CANCEL |

Listados comerciales paginados: `?page=0&size=20`; `page` comienza en 0 y `size`
admite de 1 a 100. Incluyen registros inactivos o ventas anuladas. El listado de
usuarios y los catálogos devuelven listas sin paginación.

DELETE de clientes/productos **desactiva**, sin borrar referencias históricas.
Actualizar esos registros usa PUT con todos sus campos editables y exige que
estén activos. Eliminar una cuenta de usuario sí la elimina físicamente.

Respuestas habituales: 201 para altas comerciales/catálogos, 200 para altas de
usuarios y consultas/modificaciones, 204 al desactivar, 400 para entrada inválida,
404 para registros inexistentes y 409 para conflictos. Falta de permisos devuelve
403; login incorrecto devuelve 401. No todas las rutas sin autenticación usan el
mismo código: pueden responder 401 o 403 según Spring Security.

## Flujo de usuarios y ventas

### Crear, habilitar y asignar accesos

Crea una cuenta con `POST /api/user/add`:

```json
{"username":"ana","email":"ana@example.com","password":"contraseña propia"}
```

Recibe CUSTOMER por defecto y queda habilitada si no envías estados. Para habilitar
y desbloquear una cuenta existente, usa `PUT /api/user/update`:

```json
{"username":"ana","locked":false,"disabled":false}
```

PUT conserva los campos omitidos o null. Una contraseña enviada debe ser texto
nuevo; se codifica con BCrypt. El campo singular `role` es el recomendado. El
formato anterior `roles` solo admite una entrada; una lista vacía al actualizar
no puede dejar al usuario sin rol. `additionalPermissions` reemplaza el conjunto
individual: `[]` lo vacía y omitirlo lo conserva.

Crea un rol con `POST /api/roles`:

```json
{
  "name":"VENDEDOR",
  "permissions":["CUSTOMER_READ","CUSTOMER_CREATE","PRODUCT_READ","SALE_READ","SALE_CREATE"]
}
```

Asígnalo con `POST /api/user/assignRole`:

```json
{"username":"ana","role":"VENDEDOR"}
```

El endpoint reemplaza el rol anterior. Repetir el mismo no crea otra asignación
ni modifica su fecha. Para permitir solo a ana anular ventas, utiliza
`POST /api/user/assignPermission`:

```json
{"username":"ana","permission":"SALE_CANCEL"}
```

Consulta el resultado con `GET /api/user/ana/permissions`. Para retirar la
concesión individual, usa `DELETE /api/user/ana/permissions/SALE_CANCEL`.

### Registrar una venta

Crea un cliente con `POST /api/customers`:

```json
{"name":"Cliente de ejemplo","email":"cliente@example.com","phone":"3001234567"}
```

Crea un producto con `POST /api/products`:

```json
{"name":"Teclado","sku":"TEC-001","price":125000.00,"stock":10}
```

Usa los IDs devueltos en `POST /api/sales`:

```json
{"customerId":1,"items":[{"productId":1,"quantity":2}]}
```

El servidor toma el precio del producto, calcula subtotales y total con
BigDecimal, y descuenta inventario en una transacción. Cliente y productos deben
estar activos. No acepta productos repetidos, cantidades no positivas ni stock
insuficiente. Bloquea productos en orden de ID para serializar cambios; una venta
fallida revierte también los descuentos parciales.

La venta conserva nombres y precios originales como instantáneas. Editar un
cliente o producto no altera su detalle histórico. Guarda fechas y usuario que
registra o anula. `POST /api/sales/{id}/cancel` repone existencias una sola vez,
conservando el historial; repetirlo no duplica la reposición. No se editan ni
eliminan ventas. Cada POST de creación representa una venta nueva.

El SKU es único, los precios admiten dos decimales y el stock no puede ser
negativo. Una venta admite hasta 100 productos y 1.000.000 unidades por línea.
Una cuenta CUSTOMER de seguridad y un cliente comercial son entidades distintas,
sin asociación automática. La plantilla no incluye pagos, impuestos, facturación
fiscal, monedas múltiples, devoluciones parciales ni multitenencia.

## Persistencia y estructura

| Tabla | Función |
| --- | --- |
| `"user"` | Cuenta, hash BCrypt y estados |
| `app_role`, `app_permission` | Catálogos de roles y permisos |
| `user_role` | Rol por cuenta; username único |
| `role_permission` | Permisos heredados de un rol |
| `user_permission` | Permisos individuales |
| `user_role_history` | Asignaciones retiradas por la migración de roles |
| `business_customer`, `business_product` | Clientes e inventario |
| `business_sale`, `business_sale_item` | Venta y sus líneas |

`role_permission.role_name` referencia `app_role.name` y `permission_name`
referencia `app_permission.name`; su clave compuesta evita parejas repetidas.
`user_role.role` también referencia el catálogo. Hibernate cita `"user"` para
compatibilidad con PostgreSQL.

La [migración PostgreSQL](src/main/resources/db/postgresql/single-role-and-user-permissions.sql)
normaliza cuentas antiguas con varios roles: conserva ADMIN si existe, o el rol
más antiguo; traslada permisos exclusivos de roles retirados a permisos
individuales y archiva las asignaciones retiradas. Las cuentas sin rol reciben
CUSTOMER. Se ejecuta al arrancar antes del bootstrap, puede repetirse y conserva
contraseñas, correos y estados. No importa automáticamente datos de MySQL.

```text
src/main/java/com/roles/usermanagement/
├── domain/          DTO, servicios y contratos de seguridad
├── persistance/     Entidades, repositorios, CRUD y mapeadores de seguridad
├── web/             Controladores y configuración JWT, CORS y Swagger
└── modules/
    ├── customer/    Entidad, repositorio, DTO, servicio y controlador
    ├── product/     Entidad, repositorio, DTO, servicio y controlador
    └── sale/        Venta, líneas, repositorio, DTO, servicio y controlador
```

Las nuevas entidades comerciales están en `modules`, no en `persistance/entity`.
Se utiliza Lombok, MapStruct, Spring Data JPA y Jakarta Validation.

## Frontend y Swagger

- [Swagger UI](http://localhost:8050/swagger-ui.html)
- [OpenAPI JSON](http://localhost:8050/v3/api-docs)
- [OpenAPI YAML](http://localhost:8050/v3/api-docs.yaml)

La documentación es pública. En Swagger ejecuta el login y pega el JWT en
**Authorize**, sin escribir Bearer; Swagger añade la cabecera.

El frontend está en `D:\Spring Boot Proyectos\sguridad java 21\Frontend`.
Abre su `index.html` o sírvelo con un servidor estático. En «Conexión al servidor»
usa `http://localhost:8050`, sin `/api` al final. Consulta la
[guía del frontend](../Frontend/README.md). La interfaz usa `/api/auth/me` para
mostrar las acciones disponibles; el backend verifica siempre los permisos.

## Pruebas y extensión

```powershell
.\gradlew.bat test
.\gradlew.bat test bootJar
```

El perfil test usa H2 en modo PostgreSQL y credenciales JWT propias. Las pruebas
cubren bootstrap, login, Swagger, CRUD y permisos dinámicos, sesión actual,
contraseñas BCrypt, estados de cuenta, JWT configurable, ventas concurrentes,
rollback y anulación. No necesitan modificar la base de desarrollo.

Para ejecutar contra un esquema PostgreSQL **aislado y descartable**, configura
`TEST_DB_URL` (con `currentSchema`), `TEST_DB_DRIVER=org.postgresql.Driver`,
`TEST_DB_USERNAME`, `TEST_DB_PASSWORD` y opcionalmente `TEST_DB_DDL`. Crea el esquema
antes y elimínalo después de verificar; no apuntes estas pruebas a tus datos reales.

La seguridad se verificó anteriormente en esquemas PostgreSQL aislados. Los módulos
comerciales y la configuración JWT más reciente se comprobaron con H2; la última
instancia PostgreSQL local estaba apagada. El frontend se probó contra una instancia
Spring Boot/H2 temporal, sin guardar esos registros en PostgreSQL.

Para ampliar la plantilla, toma un módulo como referencia: entidad y repositorio,
DTO validados, servicio transaccional y controlador protegido con `@PreAuthorize`.
Registra sus permisos en `UserRoles.Authority` si son parte del catálogo base o
créales entradas mediante la API si son dinámicos. Devuelve DTO en lugar de
entidades y mantiene cambios relacionados en una misma transacción.

Consulta [la guía de módulos](docs/PLANTILLA_MODULOS.md) para más ejemplos.
