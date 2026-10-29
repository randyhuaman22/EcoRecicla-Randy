# EcoRutas · aplicación móvil

Aplicación Android hecha con Kotlin Multiplatform y Compose Multiplatform. Dominio, datos y presentación viven en `shared/src/commonMain`; Android aporta SQLDelight, el cliente OkHttp y el almacenamiento cifrado de sesión.

## Configuración y ejecución

- Android Studio o Android SDK, JDK 21 y Gradle Wrapper incluido.
- Emulador con API local: `http://10.0.2.2:8080/api/v1/`.
- Cambiar el servidor con `-PecoreciclaApiBaseUrl=http://IP_DEL_SERVIDOR:8080/api/v1/` al ejecutar Gradle. En un teléfono, usar la IP accesible del equipo servidor. La URL se define una sola vez en `androidApp`.
- `./gradlew :androidApp:assembleDebug` compila el APK.
- `./gradlew :shared:jvmTest` ejecuta las pruebas comunes y la prueba SQLDelight en SQLite en memoria.

HTTP sin TLS está habilitado solo para el servidor de desarrollo local. Para otro servidor, configurar HTTPS y retirar `usesCleartextTraffic`.

## Contrato y seguridad

Consume el contrato del backend bajo `/api/v1/`: login, usuario actual, ruta asignada, puntos paginados, pesajes paginados, registro/corrección/anulación, omisión/cierre de ruta y administración de puntos/rutas. `HttpClient` único, JSON tolerante, bearer central salvo login, timeout y logging de cabeceras con autorización saneada. Errores REST se convierten a tipos de dominio; errores 400 conservan errores por campo.

La sesión se almacena en preferencias cifradas protegidas por Android Keystore. No se almacenan contraseñas. Las tablas SQLDelight y la cola se particionan por usuario. La pantalla principal observa la base local. Las acciones se muestran según los privilegios devueltos por el servicio; la autorización definitiva siempre corresponde al backend.

## Modo offline

Los registros offline reciben IDs negativos, se guardan como pendientes y se muestran desde SQLDelight. La sincronización intenta primero subir la cola y luego actualizar ruta, puntos y pesajes. Los fallos 400/403/409 quedan guardados con mensaje y validación. La conectividad recuperada inicia una sincronización; también está disponible el botón manual.

Cada alta pendiente envía una cabecera `Idempotency-Key` estable en los reintentos. Para evitar duplicados, el backend debe almacenar y respetar esa clave de forma atómica; mientras el backend no implemente ese contrato, la app por sí sola no puede garantizar que un reintento no duplique un pesaje.

## Interfaz y prueba en el teléfono

La barra inferior separa Ruta, Puntos, Administrar (solo administradores) y Perfil. Toca un punto de la ruta o del padrón para ver sus pesajes antes del formulario; toca un pesaje del historial para mostrar las acciones permitidas a tu usuario. El supervisor puede corregir los registros que permite el servicio, anular los ya sincronizados y cerrar rutas. El operario solo puede corregir sus propios registros dentro de las reglas del backend. El administrador consulta y gestiona puntos/rutas, pero no registra pesajes.

En Puntos, «Zona» filtra desde el servicio y la base local. «Buscar por empresa, dirección o material» busca en los puntos cargados en la página actual; si quieres encontrar uno de otra página, usa el filtro de zona o recorre las páginas. No confundas una dirección con el valor de Zona. Los datos guardados continúan visibles sin red.

En el detalle, el historial es desplazable; cada registro muestra bruto, tara, neto, operario y estado de sincronización. El formulario calcula el neto antes de guardarlo. El perfil muestra el resumen del último intento de sincronización. El diseño no cambia las autorizaciones del backend ni elimina la necesidad de probar el modo avión y la sincronización real.
