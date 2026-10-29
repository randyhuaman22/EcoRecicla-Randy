package pe.upeu.ecorecicla.data.repository

import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pe.upeu.ecorecicla.data.local.BaseLocal
import pe.upeu.ecorecicla.data.remote.EcoApi
import pe.upeu.ecorecicla.data.remote.dto.*
import pe.upeu.ecorecicla.domain.model.*
import pe.upeu.ecorecicla.domain.repository.RepositorioEco
import pe.upeu.ecorecicla.domain.repository.SesionSegura

class RepositorioEcoImpl(
    private val api: EcoApi,
    private val local: BaseLocal,
    private val sesionSegura: SesionSegura,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : RepositorioEco {
    override suspend fun login(usuario: String, contrasena: String): Resultado<Sesion> = when (val r = api.login(usuario, contrasena)) {
        is Resultado.Exito -> Resultado.Exito(r.valor.toDomain(Clock.System.now().epochSeconds + r.valor.expiraEnSegundos).also { sesionSegura.guardar(it) })
        is Resultado.Fallo -> r
    }
    override suspend fun usuarioActual(): Resultado<Sesion> {
        val sesion = sesionSegura.leer() ?: return Resultado.Fallo(ErrorApi.NoAutenticado("Inicia sesión para continuar."))
        return when (val r = api.actual()) {
            is Resultado.Exito -> Resultado.Exito(r.valor.toDomain(sesion.token, sesion.expiraEpochSeconds)).also { sesionSegura.guardar((it as Resultado.Exito).valor) }
            is Resultado.Fallo -> { if (r.error is ErrorApi.NoAutenticado) sesionSegura.borrar(); r }
        }
    }
    override fun observarRuta(usuario: String, fecha: String) = local.observarRuta(usuario, fecha)
    override suspend fun actualizarRuta(usuario: String, fecha: String): Resultado<RutaRecojo> = when (val r = api.ruta(fecha)) {
        is Resultado.Exito -> {
            val now = Clock.System.now().toString()
            val ruta = r.valor.toDomain(now)
            local.guardarRuta(usuario, ruta)
            local.guardarUltimaSincronizacion(usuario, now)
            Resultado.Exito(ruta)
        }
        is Resultado.Fallo -> r
    }
    override fun observarPuntos(usuario: String, zona: String, pagina: Int, tamanio: Int) = local.observarPuntos(usuario, zona, pagina, tamanio)
    override suspend fun cargarPuntos(usuario: String, zona: String?, pagina: Int, tamanio: Int): Resultado<Int> = when (val r = api.puntos(pagina, tamanio, zona)) {
        is Resultado.Exito -> { local.guardarPuntos(usuario, r.valor.contenido.map(PuntoDto::toDomain)); Resultado.Exito(r.valor.totalElementos.toInt()) }
        is Resultado.Fallo -> r
    }
    override fun observarPesajes(usuario: String, puntoId: Long) = local.observarPesajes(usuario, puntoId)
    override suspend fun cargarPesajes(usuario: String, puntoId: Long, pagina: Int, tamanio: Int): Resultado<Int> = when (val r = api.pesajes(puntoId, pagina, tamanio)) {
        is Resultado.Exito -> { local.guardarPesajes(usuario, r.valor.contenido.map(PesajeDto::toDomain)); Resultado.Exito(r.valor.totalElementos.toInt()) }
        is Resultado.Fallo -> r
    }
    override suspend fun registrarPesaje(usuario: String, rutaId: Long, puntoId: Long, formulario: PesajeFormulario): Resultado<Pesaje> {
        val body = PesajeRequestDto(formulario.pesoBruto, formulario.tara, formulario.material, formulario.observacion)
        // Persist locally before the request so the UI can observe it immediately, even offline.
        val provisional = local.guardarPesajePendiente(usuario, rutaId, puntoId, formulario)
        val operacion = local.crearPendienteParaPesaje(usuario, provisional.id, TipoOperacion.CREAR_PESAJE, rutaId, puntoId, json.encodeToString(body))
        return when (val r = api.registrar(rutaId, puntoId, body, "EcoRecicla:$usuario:${operacion.id}")) {
            is Resultado.Exito -> {
                val definitivo = r.valor.toDomain()
                local.reemplazarIdLocal(usuario, provisional.id, definitivo)
                local.borrarOperacion(operacion.id)
                Resultado.Exito(definitivo)
            }
            is Resultado.Fallo -> {
                if (r.error == ErrorApi.SinConexion || r.error == ErrorApi.TiempoAgotado) {
                    local.guardarError(operacion, r.error)
                    Resultado.Exito(provisional, pendiente = true)
                } else {
                    local.descartarPesajeProvisional(usuario, provisional.id, operacion.id)
                    r
                }
            }
        }
    }
    override suspend fun corregirPesaje(usuario: String, pesaje: Pesaje, formulario: PesajeFormulario): Resultado<Pesaje> {
        val body = PesajeRequestDto(formulario.pesoBruto, formulario.tara, formulario.material, formulario.observacion)
        if (pesaje.id < 0) {
            val cambiado = local.corregirLocal(usuario, pesaje, formulario)
            local.crearPendienteParaPesaje(usuario, pesaje.id, TipoOperacion.CORREGIR_PESAJE, pesaje.rutaId, pesaje.puntoId, json.encodeToString(body))
            return Resultado.Exito(cambiado, pendiente = true)
        }
        return when (val r = api.corregir(pesaje.id, body)) {
            is Resultado.Exito -> { val definitivo = r.valor.toDomain(); local.guardarPesajes(usuario, listOf(definitivo)); Resultado.Exito(definitivo) }
            is Resultado.Fallo -> {
                if (r.error == ErrorApi.SinConexion || r.error == ErrorApi.TiempoAgotado) {
                    val cambiado = local.corregirLocal(usuario, pesaje, formulario)
                    val operacion = local.crearPendienteParaPesaje(usuario, pesaje.id, TipoOperacion.CORREGIR_PESAJE, pesaje.rutaId, pesaje.puntoId, json.encodeToString(body))
                    local.guardarError(operacion, r.error)
                    Resultado.Exito(cambiado, pendiente = true)
                } else r
            }
        }
    }
    override suspend fun anularPesaje(usuario: String, pesaje: Pesaje, motivo: String): Resultado<Unit> {
        if (pesaje.id < 0) return Resultado.Fallo(ErrorApi.Conflicto("Sincroniza el pesaje antes de anularlo."))
        return when (val r = api.anular(pesaje.id, motivo)) {
            is Resultado.Exito -> { local.guardarPesajes(usuario, listOf(r.valor.toDomain())); Resultado.Exito(Unit) }
            is Resultado.Fallo -> {
                if (r.error == ErrorApi.SinConexion || r.error == ErrorApi.TiempoAgotado) {
                    local.anularLocal(usuario, pesaje, motivo)
                    val operacion = local.crearPendienteParaPesaje(usuario, pesaje.id, TipoOperacion.ANULAR_PESAJE, pesaje.rutaId, pesaje.puntoId, json.encodeToString(MotivoRequestDto(motivo)))
                    local.guardarError(operacion, r.error)
                    Resultado.Exito(Unit, pendiente = true)
                } else r
            }
        }
    }
    override suspend fun omitirPunto(usuario: String, rutaId: Long, puntoId: Long, motivo: String): Resultado<Unit> {
        return when (val r = api.omitir(rutaId, puntoId, motivo)) {
            is Resultado.Exito -> { local.guardarRuta(usuario, r.valor.toDomain()); Resultado.Exito(Unit) }
            is Resultado.Fallo -> {
                if (r.error == ErrorApi.SinConexion || r.error == ErrorApi.TiempoAgotado) {
                    val pending = local.encolar(OperacionPendiente(0, TipoOperacion.OMITIR_PUNTO, puntoId, rutaId, puntoId,
                        json.encodeToString(MotivoRequestDto(motivo)), Clock.System.now().toString(), usuario = usuario))
                    local.guardarError(pending, r.error)
                    Resultado.Exito(Unit, pendiente = true)
                } else r
            }
        }
    }
    override suspend fun cerrarRuta(usuario: String, rutaId: Long): Resultado<Unit> {
        return when (val r = api.cerrarRuta(rutaId)) {
            is Resultado.Exito -> { local.guardarRuta(usuario, r.valor.toDomain()); Resultado.Exito(Unit) }
            is Resultado.Fallo -> {
                if (r.error == ErrorApi.SinConexion || r.error == ErrorApi.TiempoAgotado) {
                    val pending = local.encolar(OperacionPendiente(0, TipoOperacion.CERRAR_RUTA, rutaId, rutaId, null, "{}", Clock.System.now().toString(), usuario = usuario))
                    local.guardarError(pending, r.error)
                    Resultado.Exito(Unit, pendiente = true)
                } else r
            }
        }
    }
    override suspend fun crearPunto(usuario: String, punto: PuntoCrear): Resultado<Long> = when (val r = api.crearPunto(PuntoCrearRequestDto(punto.razonSocial, punto.direccion, punto.zona, punto.materialHabitual))) {
        is Resultado.Exito -> { local.guardarPuntos(usuario, listOf(r.valor.toDomain())); Resultado.Exito(r.valor.id) }
        is Resultado.Fallo -> r
    }
    override suspend fun crearRuta(usuario: String, fecha: String, operarioId: Long, puntosIds: List<Long>): Resultado<RutaRecojo> = when (val r = api.crearRuta(RutaCrearRequestDto(fecha, operarioId, puntosIds))) {
        is Resultado.Exito -> { local.guardarRuta(usuario, r.valor.toDomain()); Resultado.Exito(r.valor.toDomain()) }
        is Resultado.Fallo -> r
    }
    override suspend fun guardarSesion(sesion: Sesion) = sesionSegura.guardar(sesion)
    override suspend fun restaurarSesion(): Resultado<Sesion?> {
        val sesion = sesionSegura.leer() ?: return Resultado.Exito(null)
        if (sesion.expiraEpochSeconds > 0 && Clock.System.now().epochSeconds >= sesion.expiraEpochSeconds) { sesionSegura.borrar(); return Resultado.Exito(null) }
        return when (val r = usuarioActual()) {
            is Resultado.Exito -> r
            is Resultado.Fallo -> if (r.error is ErrorApi.NoAutenticado) Resultado.Exito(null) else Resultado.Exito(sesion)
        }
    }
    override suspend fun cerrarSesion() = sesionSegura.borrar()
    override suspend fun hayPendientes(usuario: String) = local.cantidadPendiente(usuario)
}

class Sincronizador(
    private val api: EcoApi,
    private val local: BaseLocal,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun sincronizar(usuario: String, fecha: String): Resultado<ResumenSincronizacion> {
        var enviados = 0
        val errores = mutableMapOf<Long, ErrorApi>()
        for (op in local.pendientes(usuario)) {
            val resultado: Resultado<*> = when (op.tipo) {
                TipoOperacion.CREAR_PESAJE -> api.registrar(op.rutaId, op.puntoId!!, json.decodeFromString<PesajeRequestDto>(op.payload), "EcoRecicla:$usuario:${op.id}")
                TipoOperacion.CORREGIR_PESAJE -> api.corregir(op.entidadId!!, json.decodeFromString<PesajeRequestDto>(op.payload))
                TipoOperacion.ANULAR_PESAJE -> api.anular(op.entidadId!!, json.decodeFromString<MotivoRequestDto>(op.payload).motivo)
                TipoOperacion.OMITIR_PUNTO -> api.omitir(op.rutaId, op.puntoId!!, json.decodeFromString<MotivoRequestDto>(op.payload).motivo)
                TipoOperacion.CERRAR_RUTA -> api.cerrarRuta(op.rutaId)
            }
            when (resultado) {
                is Resultado.Exito<*> -> {
                    when (op.tipo) {
                        TipoOperacion.CREAR_PESAJE -> local.reemplazarIdLocal(usuario, op.entidadId!!, (resultado.valor as PesajeDto).toDomain())
                        TipoOperacion.CORREGIR_PESAJE, TipoOperacion.ANULAR_PESAJE -> local.guardarPesajes(usuario, listOf((resultado.valor as PesajeDto).toDomain()))
                        TipoOperacion.OMITIR_PUNTO, TipoOperacion.CERRAR_RUTA -> local.guardarRuta(usuario, (resultado.valor as RutaDto).toDomain())
                    }
                    local.borrarOperacion(op.id); enviados++
                }
                is Resultado.Fallo -> { local.guardarError(op, resultado.error); errores[op.id] = resultado.error }
            }
        }
        // Download only after the upload pass; a 401 clears at the owner layer.
        val ruta = api.ruta(fecha)
        if (ruta is Resultado.Fallo) return Resultado.Fallo(ruta.error)
        local.guardarRuta(usuario, (ruta as Resultado.Exito<RutaDto>).valor.toDomain())
        val puntos = api.puntos(0, 20, null)
        if (puntos is Resultado.Exito) local.guardarPuntos(usuario, puntos.valor.contenido.map(PuntoDto::toDomain))
        val ids = (ruta.valor as RutaDto).puntos.map { it.id }
        var recibidos = 0
        for (id in ids) {
            val page = api.pesajes(id, 0, 100)
            if (page is Resultado.Exito) { local.guardarPesajes(usuario, page.valor.contenido.map(PesajeDto::toDomain)); recibidos += page.valor.contenido.size }
        }
        local.guardarUltimaSincronizacion(usuario, Clock.System.now().toString())
        return Resultado.Exito(ResumenSincronizacion(enviados, recibidos, local.cantidadPendiente(usuario), errores))
    }
}
