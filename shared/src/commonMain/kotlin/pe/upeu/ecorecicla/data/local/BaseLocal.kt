package pe.upeu.ecorecicla.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import pe.upeu.ecorecicla.db.EcoReciclaDb
import pe.upeu.ecorecicla.db.Pesaje_local
import pe.upeu.ecorecicla.db.Ruta_punto
import pe.upeu.ecorecicla.domain.model.*

interface BaseLocal {
    fun observarRuta(usuario: String, fecha: String): Flow<RutaRecojo?>
    fun observarPuntos(usuario: String, zona: String, pagina: Int, tamanio: Int): Flow<List<PuntoAcopio>>
    fun observarPesajes(usuario: String, puntoId: Long): Flow<List<Pesaje>>
    suspend fun guardarRuta(usuario: String, ruta: RutaRecojo)
    suspend fun guardarPuntos(usuario: String, puntos: List<PuntoAcopio>)
    suspend fun guardarPesajes(usuario: String, pesajes: List<Pesaje>)
    suspend fun guardarPesajePendiente(usuario: String, rutaId: Long, puntoId: Long, formulario: PesajeFormulario): Pesaje
    suspend fun corregirLocal(usuario: String, pesaje: Pesaje, formulario: PesajeFormulario): Pesaje
    suspend fun anularLocal(usuario: String, pesaje: Pesaje, motivo: String): Pesaje
    suspend fun buscarPesaje(usuario: String, id: Long): Pesaje?
    suspend fun encolar(operacion: OperacionPendiente): OperacionPendiente
    suspend fun crearPendienteParaPesaje(usuario: String, pesajeId: Long, tipo: TipoOperacion, rutaId: Long, puntoId: Long?, payload: String): OperacionPendiente
    suspend fun pendientes(usuario: String): List<OperacionPendiente>
    suspend fun guardarError(operacion: OperacionPendiente, error: ErrorApi)
    suspend fun borrarOperacion(operacionId: Long)
    suspend fun reemplazarIdLocal(usuario: String, temporalId: Long, pesaje: Pesaje)
    suspend fun guardarUltimaSincronizacion(usuario: String, fecha: String)
    suspend fun ultimaSincronizacion(usuario: String): Flow<String?>
    suspend fun cantidadPendiente(usuario: String): Int
}

class BaseLocalSqlDelight(
    private val database: EcoReciclaDb,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseLocal {
    private val q get() = database.localCacheQueries
    private val json = Json { ignoreUnknownKeys = true }

    override fun observarRuta(usuario: String, fecha: String): Flow<RutaRecojo?> =
        q.observeRouteByDate(usuario, fecha).asFlow().mapToOneOrNull(dispatcher).flatMapLatest { ruta ->
            if (ruta == null) flowOf(null)
            else combine(
                q.observeRoutePoints(usuario, ruta.ruta_id).asFlow().mapToList(dispatcher),
                q.readSyncTime(usuario).asFlow().mapToOneOrNull(dispatcher),
            ) { puntos, syncTime -> RutaRecojo(ruta.ruta_id, ruta.fecha, ruta.estado, puntos.map(Ruta_punto::toDomain), syncTime) }
        }

    override fun observarPuntos(usuario: String, zona: String, pagina: Int, tamanio: Int): Flow<List<PuntoAcopio>> =
        q.observePoints(usuario, zona, zona, tamanio.toLong(), (pagina * tamanio).toLong())
            .asFlow().mapToList(dispatcher).map { rows -> rows.map { PuntoAcopio(it.punto_id, it.razon_social, it.direccion, it.zona, it.material_habitual, 0, false) } }

    override fun observarPesajes(usuario: String, puntoId: Long): Flow<List<Pesaje>> =
        q.observePesajes(usuario, puntoId).asFlow().mapToList(dispatcher).map { rows -> rows.map(Pesaje_local::toDomain) }

    override suspend fun guardarRuta(usuario: String, ruta: RutaRecojo) {
        database.transaction {
            q.insertRoute(usuario, ruta.id, ruta.fecha, ruta.estado)
            q.deleteRoutePoints(usuario, ruta.id)
            ruta.puntos.forEach { punto ->
                q.insertRoutePoint(usuario, ruta.id, punto.id, punto.razonSocial, punto.direccion, punto.zona,
                    punto.materialHabitual, punto.secuencia.toLong(), punto.atendido.toLong(), punto.motivoOmission)
            }
            ruta.ultimaSincronizacion?.let { q.saveSyncTime(usuario, it) }
        }
    }

    override suspend fun guardarPuntos(usuario: String, puntos: List<PuntoAcopio>) {
        database.transaction { puntos.forEach { q.upsertPoint(usuario, it.id, it.razonSocial, it.direccion, it.zona, it.materialHabitual) } }
    }

    override suspend fun guardarPesajes(usuario: String, pesajes: List<Pesaje>) {
        database.transaction { pesajes.forEach { q.insertPesaje(usuario, it.id, it.rutaId, it.puntoId, it.fechaHora, it.material,
            it.pesoBruto, it.tara, it.pesoNeto, it.observacion, it.operario, it.estado.valor(), it.motivoAnulacion, it.pendiente.toLong()) } }
    }

    override suspend fun guardarPesajePendiente(usuario: String, rutaId: Long, puntoId: Long, formulario: PesajeFormulario): Pesaje {
        lateinit var pesaje: Pesaje
        database.transaction {
            val id = q.nextNegativePesajeId(usuario).executeAsOne()
            pesaje = Pesaje(id, puntoId, kotlinx.datetime.Clock.System.now().toString(), formulario.material,
                formulario.pesoBruto, formulario.tara, formulario.pesoBruto - formulario.tara, usuario,
                EstadoPesaje.Registrado, true, rutaId, formulario.observacion)
            q.insertPesaje(usuario, pesaje.id, rutaId, puntoId, pesaje.fechaHora, pesaje.material, pesaje.pesoBruto,
                pesaje.tara, pesaje.pesoNeto, pesaje.observacion, usuario, "REGISTRADO", null, 1L)
        }
        return pesaje
    }

    override suspend fun corregirLocal(usuario: String, pesaje: Pesaje, formulario: PesajeFormulario): Pesaje {
        val cambiado = pesaje.copy(fechaHora = kotlinx.datetime.Clock.System.now().toString(), material = formulario.material,
            pesoBruto = formulario.pesoBruto, tara = formulario.tara, pesoNeto = formulario.pesoBruto - formulario.tara,
            observacion = formulario.observacion, pendiente = true)
        q.updatePesaje(cambiado.rutaId, cambiado.puntoId, cambiado.fechaHora, cambiado.material, cambiado.pesoBruto,
            cambiado.tara, cambiado.pesoNeto, cambiado.observacion, cambiado.operario, cambiado.estado.valor(),
            cambiado.motivoAnulacion, 1L, usuario, cambiado.id)
        return cambiado
    }

    override suspend fun anularLocal(usuario: String, pesaje: Pesaje, motivo: String): Pesaje {
        val cambiado = pesaje.copy(estado = EstadoPesaje.Anulado(motivo), motivoAnulacion = motivo, pendiente = true)
        q.updatePesaje(cambiado.rutaId, cambiado.puntoId, cambiado.fechaHora, cambiado.material, cambiado.pesoBruto,
            cambiado.tara, cambiado.pesoNeto, cambiado.observacion, cambiado.operario, "ANULADO", motivo, 1L, usuario, cambiado.id)
        return cambiado
    }

    override suspend fun buscarPesaje(usuario: String, id: Long): Pesaje? =
        q.findPesajeById(usuario, id).executeAsOneOrNull()?.toDomain()

    override suspend fun encolar(operacion: OperacionPendiente): OperacionPendiente {
        val id = if (operacion.id > 0) operacion.id else q.nextOperationId().executeAsOne()
        q.insertPending(operacion_id = id, usuario_key = operacion.usuario, tipo = operacion.tipo.name, entidad_id = operacion.entidadId,
            ruta_id = operacion.rutaId, punto_id = operacion.puntoId, payload = operacion.payload, fecha_cambio = operacion.fechaCambio,
            codigo_error = null, mensaje_error = operacion.mensajeError,
            errores_validacion = json.encodeToString(operacion.erroresValidacion))
        return operacion.copy(id = id)
    }

    override suspend fun crearPendienteParaPesaje(usuario: String, pesajeId: Long, tipo: TipoOperacion, rutaId: Long, puntoId: Long?, payload: String): OperacionPendiente {
        val existente = if (tipo == TipoOperacion.CORREGIR_PESAJE) q.findPendingCreate(usuario, pesajeId).executeAsOneOrNull() else null
        if (existente != null) {
            q.updatePendingPayload(payload, kotlinx.datetime.Clock.System.now().toString(), existente.operacion_id)
            return OperacionPendiente(existente.operacion_id, TipoOperacion.CREAR_PESAJE, pesajeId, rutaId, puntoId, payload, kotlinx.datetime.Clock.System.now().toString())
        }
        return encolar(OperacionPendiente(0L, tipo, pesajeId, rutaId, puntoId, payload, kotlinx.datetime.Clock.System.now().toString(), usuario = usuario))
    }

    override suspend fun pendientes(usuario: String): List<OperacionPendiente> =
        q.observePending(usuario).executeAsList().map { row ->
            OperacionPendiente(row.operacion_id, TipoOperacion.valueOf(row.tipo), row.entidad_id, row.ruta_id, row.punto_id,
                row.payload, row.fecha_cambio, row.mensaje_error,
                row.errores_validacion?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrDefault(emptyMap()) }.orEmpty(), usuario)
        }

    override suspend fun guardarError(operacion: OperacionPendiente, error: ErrorApi) {
        val mensaje = when (error) {
            is ErrorApi.Validacion -> error.mensaje
            is ErrorApi.NoAutenticado -> error.mensaje
            is ErrorApi.SinPrivilegio -> error.mensaje
            is ErrorApi.NoEncontrado -> error.mensaje
            is ErrorApi.Conflicto -> error.mensaje
            is ErrorApi.Servidor -> error.mensaje
            ErrorApi.SinConexion -> "Sin conexión; se intentará más tarde."
            ErrorApi.TiempoAgotado -> "La solicitud agotó el tiempo de espera."
            is ErrorApi.Desconocido -> error.mensaje
        }
        val campos = (error as? ErrorApi.Validacion)?.campos.orEmpty()
        q.failPending(error.codigo().toLong(), mensaje, json.encodeToString(campos), operacion.id)
    }

    override suspend fun borrarOperacion(operacionId: Long) { q.deletePending(operacionId) }

    override suspend fun reemplazarIdLocal(usuario: String, temporalId: Long, pesaje: Pesaje) {
        database.transaction {
            q.deletePesaje(usuario, temporalId)
            q.insertPesaje(usuario, pesaje.id, pesaje.rutaId, pesaje.puntoId, pesaje.fechaHora, pesaje.material, pesaje.pesoBruto,
                pesaje.tara, pesaje.pesoNeto, pesaje.observacion, pesaje.operario, pesaje.estado.valor(), pesaje.motivoAnulacion, 0L)
            q.updatePendingEntityId(pesaje.id, usuario, temporalId)
        }
    }

    override suspend fun guardarUltimaSincronizacion(usuario: String, fecha: String) { q.saveSyncTime(usuario, fecha) }

    override suspend fun ultimaSincronizacion(usuario: String): Flow<String?> =
        q.readSyncTime(usuario).asFlow().mapToOneOrNull(dispatcher)

    override suspend fun cantidadPendiente(usuario: String): Int = q.countPending(usuario).executeAsOne().toInt()
}

private fun Ruta_punto.toDomain() = PuntoAcopio(punto_id, razon_social, direccion, zona, material_habitual, secuencia.toInt(), atendido != 0L, motivo_omision)

private fun Pesaje_local.toDomain() = Pesaje(id, punto_id, fecha_hora, material, peso_bruto, tara, peso_neto, operario,
    if (estado == "ANULADO") EstadoPesaje.Anulado(motivo_anulacion.orEmpty()) else EstadoPesaje.Registrado,
    pendiente != 0L, ruta_id, observacion, motivo_anulacion)

private fun EstadoPesaje.valor() = if (this is EstadoPesaje.Anulado) "ANULADO" else "REGISTRADO"
private fun Boolean.toLong() = if (this) 1L else 0L
private fun ErrorApi.codigo() = when (this) {
    is ErrorApi.Validacion -> 400
    is ErrorApi.NoAutenticado -> 401
    is ErrorApi.SinPrivilegio -> 403
    is ErrorApi.NoEncontrado -> 404
    is ErrorApi.Conflicto -> 409
    is ErrorApi.Servidor -> codigo
    ErrorApi.SinConexion -> 0
    ErrorApi.TiempoAgotado -> 408
    is ErrorApi.Desconocido -> 500
}
