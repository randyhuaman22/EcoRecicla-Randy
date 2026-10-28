package pe.upeu.ecorecicla.data.remote

import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import pe.upeu.ecorecicla.data.remote.dto.ErrorDto
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.Resultado

object MapeadorErroresHttp {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun traducir(codigo: Int, contenido: String): ErrorApi {
        val dto = runCatching { json.decodeFromString<ErrorDto>(contenido) }.getOrNull()
        val mensaje = dto?.message?.takeIf(String::isNotBlank) ?: mensajeGenerico(codigo)
        return when (codigo) {
            400 -> ErrorApi.Validacion(mensaje, dto?.validationErrors.orEmpty())
            401 -> ErrorApi.NoAutenticado(mensaje)
            403 -> ErrorApi.SinPrivilegio(mensaje)
            404 -> ErrorApi.NoEncontrado(mensaje)
            409 -> ErrorApi.Conflicto(mensaje)
            in 500..599 -> ErrorApi.Servidor("El servidor no pudo completar la solicitud.", codigo)
            else -> ErrorApi.Desconocido(mensaje)
        }
    }

    fun traducirExcepcion(error: Throwable): ErrorApi = when {
        error is CancellationException -> throw error
        error.enCadena { it is HttpRequestTimeoutException || it::class.simpleName.orEmpty().contains("Timeout", true) } -> ErrorApi.TiempoAgotado
        error is SerializationException -> ErrorApi.Desconocido("La respuesta del servidor no tiene el formato esperado.")
        error.enCadena {
            val tipo = it::class.simpleName.orEmpty()
            tipo.contains("UnknownHost") || tipo.contains("ConnectException") || tipo.contains("IOException") ||
                it.message.orEmpty().contains("UnknownHost", true) || it.message.orEmpty().contains("connection refused", true)
        } -> ErrorApi.SinConexion
        else -> ErrorApi.Desconocido(error.message ?: "No se pudo completar la operación.")
    }

    private fun Throwable.enCadena(coincide: (Throwable) -> Boolean): Boolean {
        var actual: Throwable? = this
        var profundidad = 0
        while (actual != null && profundidad++ < 12) {
            if (coincide(actual)) return true
            actual = actual.cause
        }
        return false
    }

    private fun mensajeGenerico(codigo: Int) = when (codigo) {
        400 -> "Revisa los datos ingresados."
        401 -> "La sesión venció. Inicia sesión nuevamente."
        403 -> "No tienes permiso para realizar esta acción."
        404 -> "El recurso ya no está disponible."
        409 -> "La operación no se puede completar en el estado actual."
        else -> "No se pudo completar la solicitud."
    }
}

internal inline fun <T> capturarResultado(block: () -> T): Resultado<T> =
    try { Resultado.Exito(block()) } catch (t: Throwable) { Resultado.Fallo(MapeadorErroresHttp.traducirExcepcion(t)) }

