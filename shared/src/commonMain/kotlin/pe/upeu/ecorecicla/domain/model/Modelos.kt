package pe.upeu.ecorecicla.domain.model

data class PuntoAcopio(
    val id: Long,
    val razonSocial: String,
    val direccion: String,
    val zona: String,
    val materialHabitual: String,
    val secuencia: Int,
    val atendido: Boolean,
    val motivoOmission: String? = null,
)

sealed interface EstadoPesaje {
    data object Registrado : EstadoPesaje
    data class Anulado(val motivo: String) : EstadoPesaje
}

data class Pesaje(
    val id: Long,
    val puntoId: Long,
    val fechaHora: String,
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val pesoNeto: Double,
    val operario: String,
    val estado: EstadoPesaje,
    val pendiente: Boolean,
    val rutaId: Long = 0L,
    val observacion: String = "",
    val motivoAnulacion: String? = null,
)

data class Sesion(
    val token: String,
    val usuario: String,
    val rol: String,
    val privilegios: Set<String>,
    val nombre: String = usuario,
    val expiraEpochSeconds: Long = 0L,
)

data class RutaRecojo(
    val id: Long,
    val fecha: String,
    val estado: String,
    val puntos: List<PuntoAcopio>,
    val ultimaSincronizacion: String? = null,
)

data class Pagina<T>(
    val contenido: List<T>,
    val pagina: Int,
    val tamanio: Int,
    val totalElementos: Long,
    val totalPaginas: Int,
    val ultima: Boolean,
)

data class PesajeFormulario(
    val pesoBruto: Double,
    val tara: Double,
    val material: String,
    val observacion: String,
)

data class PuntoCrear(
    val razonSocial: String,
    val direccion: String,
    val zona: String,
    val materialHabitual: String,
)

enum class TipoOperacion { CREAR_PESAJE, CORREGIR_PESAJE, ANULAR_PESAJE, OMITIR_PUNTO, CERRAR_RUTA }

data class OperacionPendiente(
    val id: Long,
    val tipo: TipoOperacion,
    val entidadId: Long?,
    val rutaId: Long,
    val puntoId: Long?,
    val payload: String,
    val fechaCambio: String,
    val mensajeError: String? = null,
    val erroresValidacion: Map<String, String> = emptyMap(),
    val usuario: String = "",
)

data class ResumenSincronizacion(
    val enviados: Int,
    val recibidos: Int,
    val pendientes: Int,
    val errores: Map<Long, ErrorApi> = emptyMap(),
)

sealed interface ErrorApi {
    data class Validacion(val mensaje: String, val campos: Map<String, String>) : ErrorApi
    data class NoAutenticado(val mensaje: String) : ErrorApi
    data class SinPrivilegio(val mensaje: String) : ErrorApi
    data class NoEncontrado(val mensaje: String) : ErrorApi
    data class Conflicto(val mensaje: String) : ErrorApi
    data class Servidor(val mensaje: String, val codigo: Int = 500) : ErrorApi
    data object SinConexion : ErrorApi
    data object TiempoAgotado : ErrorApi
    data class Desconocido(val mensaje: String) : ErrorApi
}

sealed interface Resultado<out T> {
    data class Exito<T>(val valor: T, val pendiente: Boolean = false) : Resultado<T>
    data class Fallo(val error: ErrorApi) : Resultado<Nothing>
}

