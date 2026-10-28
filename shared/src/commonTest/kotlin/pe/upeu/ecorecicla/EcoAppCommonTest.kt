package pe.upeu.ecorecicla

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.contentType
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import pe.upeu.ecorecicla.data.local.BaseLocal
import pe.upeu.ecorecicla.data.remote.EcoApi
import pe.upeu.ecorecicla.data.remote.MapeadorErroresHttp
import pe.upeu.ecorecicla.data.remote.dto.*
import pe.upeu.ecorecicla.data.repository.RepositorioEcoImpl
import pe.upeu.ecorecicla.data.repository.Sincronizador
import pe.upeu.ecorecicla.domain.model.*
import pe.upeu.ecorecicla.domain.repository.SesionSegura
import pe.upeu.ecorecicla.domain.usecase.capacidades
import kotlin.test.*

class EcoAppCommonTest {
    @Test fun dtoSeConvierteAlDominioSinSerializarDominio() {
        val dto = PuntoRutaDto(7, "Mercado", "Av. Central", "Centro", "Cartón", 3, true, "cerrado")
        val model = dto.toDomain()
        assertEquals(7L, model.id)
        assertEquals(3, model.secuencia)
        assertTrue(model.atendido)
        assertEquals("cerrado", model.motivoOmission)
    }

    @Test fun respuesta400SeTraduceConErroresPorCampo() {
        val e = MapeadorErroresHttp.traducir(400, """{"message":"Datos inválidos","validationErrors":{"tara":"No puede ser negativa"}}""")
        assertIs<ErrorApi.Validacion>(e)
        assertEquals("No puede ser negativa", e.campos["tara"])
    }

    @Test fun capacidadesRespetanRolYPrivilegios() {
        val operario = capacidades(Sesion("t", "op", "OPERARIO", setOf("RUTA_CERRAR", "PESAJE_ANULAR")))
        assertTrue(operario.puedeRegistrarPesajes)
        assertFalse(operario.puedeAnularPesajes)
        assertFalse(operario.puedeCerrarRuta)
        assertTrue(operario.puedeOmitirPunto)
        val admin = capacidades(Sesion("t", "admin", "ADMINISTRADOR", setOf("PESAJE_REGISTRAR")))
        assertTrue(admin.puedeAdministrar)
        assertFalse(admin.puedeRegistrarPesajes)
    }

    @Test fun sinRedGuardaPesajeComoOperacionPendienteConIdLocal() = runBlocking {
        val engine = MockEngine { throw UnknownHostException() }
        val client = testClient(engine)
        val local = FakeLocal()
        val repo = RepositorioEcoImpl(EcoApi(client), local, FakeSession())
        val result = repo.registrarPesaje("operador-a", 4, 7, PesajeFormulario(20.0, 2.0, "Cartón", "observado"))
        assertTrue(result is Resultado.Exito<Pesaje>, "resultado=$result")
        result as Resultado.Exito<Pesaje>
        assertEquals(-1L, result.valor.id)
        assertTrue(result.valor.pendiente)
        assertEquals(TipoOperacion.CREAR_PESAJE, local.queue.single().tipo)
        assertEquals("operador-a", local.queue.single().usuario)
        client.close()
    }

    @Test fun sincronizadorSubePendientesAntesDeDescargar() = runBlocking {
        val orden = mutableListOf<String>()
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            orden += if (path.endsWith("/cierre")) "subir" else "descargar"
            val body = when {
                path.endsWith("/cierre") || path.endsWith("/asignada") -> """{"id":2,"fecha":"2026-10-28","estado":"CERRADA","puntos":[]}"""
                path.endsWith("/puntos") -> """{"contenido":[],"pagina":0,"tamanio":20,"totalElementos":0,"totalPaginas":0,"ultima":true}"""
                else -> "{}"
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = testClient(engine)
        val local = FakeLocal().apply { queue += OperacionPendiente(1, TipoOperacion.CERRAR_RUTA, 2, 2, null, "{}", "2026-10-28", usuario = "a") }
        val result = Sincronizador(EcoApi(client), local).sincronizar("a", "2026-10-28")
        assertIs<Resultado.Exito<ResumenSincronizacion>>(result)
        assertEquals(listOf("subir", "descargar", "descargar"), orden)
        client.close()
    }
}

private fun testClient(engine: MockEngine) = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    defaultRequest { url("http://test.local/api/v1/"); contentType(ContentType.Application.Json) }
}

private class UnknownHostException : Exception("No se pudo resolver el servidor")

private class FakeSession : SesionSegura {
    var value: Sesion? = null
    override suspend fun guardar(sesion: Sesion) { value = sesion }
    override suspend fun leer() = value
    override suspend fun borrar() { value = null }
}

private class FakeLocal : BaseLocal {
    val queue = mutableListOf<OperacionPendiente>()
    override fun observarRuta(usuario: String, fecha: String) = kotlinx.coroutines.flow.flowOf<RutaRecojo?>(null)
    override fun observarPuntos(usuario: String, zona: String, pagina: Int, tamanio: Int) = kotlinx.coroutines.flow.flowOf(emptyList<PuntoAcopio>())
    override fun observarPesajes(usuario: String, puntoId: Long) = kotlinx.coroutines.flow.flowOf(emptyList<Pesaje>())
    override suspend fun guardarRuta(usuario: String, ruta: RutaRecojo) {}
    override suspend fun guardarPuntos(usuario: String, puntos: List<PuntoAcopio>) {}
    override suspend fun guardarPesajes(usuario: String, pesajes: List<Pesaje>) {}
    override suspend fun guardarPesajePendiente(usuario: String, rutaId: Long, puntoId: Long, formulario: PesajeFormulario) = Pesaje(-1, puntoId, "2026-10-28T00:00:00Z", formulario.material, formulario.pesoBruto, formulario.tara, formulario.pesoBruto - formulario.tara, usuario, EstadoPesaje.Registrado, true, rutaId, formulario.observacion)
    override suspend fun corregirLocal(usuario: String, pesaje: Pesaje, formulario: PesajeFormulario) = pesaje
    override suspend fun anularLocal(usuario: String, pesaje: Pesaje, motivo: String) = pesaje
    override suspend fun buscarPesaje(usuario: String, id: Long): Pesaje? = null
    override suspend fun encolar(operacion: OperacionPendiente): OperacionPendiente = operacion.copy(id = (queue.size + 1).toLong()).also(queue::add)
    override suspend fun crearPendienteParaPesaje(usuario: String, pesajeId: Long, tipo: TipoOperacion, rutaId: Long, puntoId: Long?, payload: String) = encolar(OperacionPendiente(0, tipo, pesajeId, rutaId, puntoId, payload, "2026-10-28", usuario = usuario))
    override suspend fun pendientes(usuario: String) = queue.filter { it.usuario == usuario }
    override suspend fun guardarError(operacion: OperacionPendiente, error: ErrorApi) {}
    override suspend fun borrarOperacion(operacionId: Long) { queue.removeAll { it.id == operacionId } }
    override suspend fun reemplazarIdLocal(usuario: String, temporalId: Long, pesaje: Pesaje) {}
    override suspend fun guardarUltimaSincronizacion(usuario: String, fecha: String) {}
    override suspend fun ultimaSincronizacion(usuario: String) = kotlinx.coroutines.flow.flowOf<String?>(null)
    override suspend fun cantidadPendiente(usuario: String) = queue.count { it.usuario == usuario }
}
