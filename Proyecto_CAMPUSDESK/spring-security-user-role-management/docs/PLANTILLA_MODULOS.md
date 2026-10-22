# Plantilla: clientes, productos y ventas protegidos

Los mÃ³dulos estÃ¡n en `modules/customer`, `modules/product` y `modules/sale`.
Cada mÃ³dulo separa entidad, repositorio, DTO de entrada, DTO de respuesta,
servicio transaccional y controlador REST. Se reutilizan JWT, roles dinÃ¡micos,
permisos individuales, catÃ¡logo y manejo de errores de la seguridad existente.

## EjecuciÃ³n

Arranca PostgreSQL, configura DB_URL, DB_USERNAME y DB_PASSWORD y ejecuta
`.\gradlew.bat bootRun`. Hibernate crea las tablas nuevas sin borrar las cuentas
existentes. El bootstrap agrega los permisos que falten a ADMIN; no los concede
a CUSTOMER. Abre http://localhost:8050/swagger-ui.html, inicia sesiÃ³n y usa Authorize.

## Permisos y rutas

| MÃ³dulo | Rutas | Permisos |
| --- | --- | --- |
| Clientes | GET /api/customers y /api/customers/{id} | CUSTOMER_READ |
| Clientes | POST /api/customers | CUSTOMER_CREATE |
| Clientes | PUT /api/customers/{id} | CUSTOMER_UPDATE |
| Clientes | DELETE /api/customers/{id} | CUSTOMER_DELETE |
| Productos | GET /api/products y /api/products/{id} | PRODUCT_READ |
| Productos | POST /api/products | PRODUCT_CREATE |
| Productos | PUT /api/products/{id} | PRODUCT_UPDATE |
| Productos | DELETE /api/products/{id} | PRODUCT_DELETE |
| Ventas | GET /api/sales y /api/sales/{id} | SALE_READ |
| Ventas | POST /api/sales | SALE_CREATE |
| Ventas | POST /api/sales/{id}/cancel | SALE_CANCEL |

Listados paginados: `?page=0&size=20`, mÃ¡ximo 100. DELETE desactiva clientes y
productos; mantiene referencias de ventas. Una cuenta de seguridad CUSTOMER y
un cliente comercial son conceptos independientes, sin asociaciÃ³n automÃ¡tica.
Los permisos son globales al mÃ³dulo, sin restricciones de propietario o empresa.

## Flujo de ejemplo

1. POST /api/customers:

```json
{"name":"Cliente de ejemplo","email":"cliente@example.com","phone":"3001234567"}
```

2. POST /api/products:

```json
{"name":"Teclado","sku":"TEC-001","price":125000.00,"stock":10}
```

3. Usa los IDs devueltos en POST /api/sales:

```json
{"customerId":1,"items":[{"productId":1,"quantity":2}]}
```

El servidor toma el precio actual del producto, calcula subtotales y total con
BigDecimal y descuenta 2 unidades. No confÃ­a en precios o totales enviados por el
cliente. Conserva nombre del cliente, nombre y precio de los productos como
instantÃ¡neas; editar el catÃ¡logo no cambia las ventas anteriores. Registra el
usuario autenticado y las fechas de creaciÃ³n y anulaciÃ³n. No elimina ni permite
editar ventas: anularlas repone inventario y mantiene el historial. Repetir la
anulaciÃ³n no repone otra vez. Cada POST de venta representa una venta nueva.

Se rechazan cantidades cero/negativas, productos repetidos, precios no positivos
o con mÃ¡s de dos decimales, stock negativo, referencias inexistentes y registros
inactivos. SKU es Ãºnico. Las ventas limitan 100 productos y 1.000.000 unidades
por lÃ­nea. Las operaciones bloquean productos en orden de ID para serializar el
inventario. Una venta fallida revierte todo; dos ventas no pueden consumir las
mismas existencias. La anulaciÃ³n tambiÃ©n es transaccional.

## Rol de vendedor y permisos individuales

Crea con POST /api/roles, usando token ADMIN:

```json
{
  "name":"VENDEDOR",
  "permissions":["CUSTOMER_READ","CUSTOMER_CREATE","PRODUCT_READ","SALE_READ","SALE_CREATE"]
}
```

Asigna ese rol con POST /api/user/assignRole:

```json
{"username":"ana","role":"VENDEDOR"}
```

Para que solo ana pueda anular ventas, POST /api/user/assignPermission:

```json
{"username":"ana","permission":"SALE_CANCEL"}
```

Otros vendedores no recibirÃ¡n ese permiso. Cambios de permisos y bloqueo o
inhabilitaciÃ³n de la cuenta se verifican con cada solicitud, incluso si el JWT
ya habÃ­a sido emitido. Usuarios eliminados no conservan acceso con sus tokens.

## Extender la plantilla

Para agregar un mÃ³dulo, usa uno existente como referencia: entidad y repositorio,
DTO con jakarta.validation, servicio transaccional y controlador con
@PreAuthorize("hasAuthority('MI_MODULO_READ')"). Registra permisos base en
UserRoles.Authority o crea permisos dinÃ¡micamente mediante /api/permissions.
El catÃ¡logo por sÃ­ solo no crea reglas ni operaciones. Devuelve DTO, no entidades,
y usa BigDecimal para importes. MantÃ©n las modificaciones de inventario dentro de
una transacciÃ³n y adquiere los bloqueos siempre en el mismo orden.

Respuestas: 201 al crear, 200 al consultar/actualizar/anular, 204 al desactivar,
400 para entrada invÃ¡lida, 404 para registros inexistentes, 409 para conflictos
(SKU duplicado, stock insuficiente o registros inactivos), 403 para falta de
permisos. Endpoints protegidos sin autenticaciÃ³n rechazan la solicitud.

Estos mÃ³dulos son una base comercial simple: no incluyen pagos, impuestos,
facturaciÃ³n fiscal, mÃºltiples monedas, devoluciones parciales ni multitenencia.
La configuraciÃ³n JWT original conserva su clave de desarrollo en JwtUtil; debe
adaptarse a secretos del entorno para un despliegue real.

## VerificaciÃ³n

`.\gradlew.bat test bootJar` ejecuta nueve pruebas de integraciÃ³n con H2 en modo
PostgreSQL: catÃ¡logo, CRUD de seguridad, Swagger, clientes/productos, permisos
individuales, estados de cuenta, ventas concurrentes, rollback y anulaciÃ³n.
La comprobaciÃ³n de esta ampliaciÃ³n con PostgreSQL real quedÃ³ pendiente porque
la instancia local no estaba disponible; las pruebas anteriores de seguridad
sÃ­ se habÃ­an ejecutado contra PostgreSQL.
