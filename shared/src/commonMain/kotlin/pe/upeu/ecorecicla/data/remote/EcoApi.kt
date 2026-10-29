package pe.upeu.ecorecicla.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import pe.upeu.ecorecicla.data.remote.dto.*
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.Resultado

class EcoApi(private val cliente: HttpClient) {
    suspend fun login(usuario: String, contrasena: String) = ejecutar<LoginResponseDto> {
        cliente.post("auth/login") { setBody(LoginRequestDto(usuario, contrasena)) }
    }
    suspend fun actual() = ejecutar<UsuarioActualDto> { cliente.get("usuarios/actual") }
    suspend fun ruta(fecha: String) = ejecutar<RutaDto> {
        cliente.get("rutas/asignada") { parameter("fecha", fecha) }
    }
    suspend fun puntos(pagina: Int, tamanio: Int, zona: String?) = ejecutar<PaginaDto<PuntoDto>> {
        cliente.get("puntos") {
            parameter("pagina", pagina); parameter("tamanio", tamanio)
            zona?.takeIf(String::isNotBlank)?.let { parameter("zona", it) }
        }
    }
    suspend fun pesajes(puntoId: Long, pagina: Int, tamanio: Int) = ejecutar<PaginaDto<PesajeDto>> {
        cliente.get("puntos/$puntoId/pesajes") { parameter("pagina", pagina); parameter("tamanio", tamanio) }
    }
    suspend fun registrar(rutaId: Long, puntoId: Long, body: PesajeRequestDto, claveIdempotencia: String? = null) = ejecutar<PesajeDto> {
        cliente.post("rutas/$rutaId/puntos/$puntoId/pesajes") {
            claveIdempotencia?.let { header("Idempotency-Key", it) }
            setBody(body)
        }
    }
    suspend fun corregir(id: Long, body: PesajeRequestDto) = ejecutar<PesajeDto> {
        cliente.put("pesajes/$id") { setBody(body) }
    }
    suspend fun anular(id: Long, motivo: String) = ejecutar<PesajeDto> {
        cliente.post("pesajes/$id/anulacion") { setBody(MotivoRequestDto(motivo)) }
    }
    suspend fun omitir(rutaId: Long, puntoId: Long, motivo: String) = ejecutar<RutaDto> {
        cliente.post("rutas/$rutaId/puntos/$puntoId/omision") { setBody(MotivoRequestDto(motivo)) }
    }
    suspend fun cerrarRuta(rutaId: Long) = ejecutar<RutaDto> { cliente.post("rutas/$rutaId/cierre") }
    suspend fun crearPunto(body: PuntoCrearRequestDto) = ejecutar<PuntoDto> {
        cliente.post("puntos") { setBody(body) }
    }
    suspend fun crearRuta(body: RutaCrearRequestDto) = ejecutar<RutaDto> {
        cliente.post("rutas") { setBody(body) }
    }

    private suspend inline fun <reified T> ejecutar(crossinline request: suspend () -> HttpResponse): Resultado<T> =
        try {
            val response = request()
            if (response.status.value in 200..299) Resultado.Exito(response.body<T>())
            else Resultado.Fallo(MapeadorErroresHttp.traducir(response.status.value, response.bodyAsText()))
        } catch (error: Throwable) {
            Resultado.Fallo(MapeadorErroresHttp.traducirExcepcion(error))
        }
}

