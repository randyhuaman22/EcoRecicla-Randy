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

La sesión se almacena en preferencias cifradas protegidas por Android Keystore. No se almacenan contraseñas. Las tablas SQLDelight y la cola se particionan por usuario. La pantalla principal observa la base local. OPERARIO registra pesajes; SUPERVISOR ve las acciones autorizadas por privilegio; ADMINISTRADOR crea puntos y rutas y no registra pesajes. El backend sigue siendo la autoridad de autorización.

## Modo offline

Los registros offline reciben IDs negativos, se guardan como pendientes y se muestran desde SQLDelight. La sincronización intenta primero subir la cola y luego actualizar ruta, puntos y pesajes. Los fallos 400/403/409 quedan guardados con mensaje y validación. La conectividad recuperada inicia una sincronización; también está disponible el botón manual.

El backend no ofrece clave idempotente para registrar pesajes. Si el servidor alcanza a crear un registro y se pierde la respuesta antes de que la app reciba el ID definitivo, un reintento puede duplicarlo; la app conserva la operación y no inventa una ruta alternativa.
