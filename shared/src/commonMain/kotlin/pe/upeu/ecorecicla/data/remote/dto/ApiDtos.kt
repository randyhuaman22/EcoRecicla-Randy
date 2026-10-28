package pe.upeu.ecorecicla.data.remote.dto

import kotlinx.serialization.Serializable
import pe.upeu.ecorecicla.domain.model.*

@Serializable data class LoginRequestDto(val usuario: String, val contrasena: String)
@Serializable data class LoginResponseDto(
    val token: String,
    val tipo: String,
    val expiraEnSegundos: Long,
    val nombre: String,
    val usuario: String,
    val rol: String,
    val privilegios: Set<String>,
)

@Serializable data class UsuarioActualDto(val id: Long, val usuario: String, val nombre: String, val rol: String, val privilegios: Set<String>)
@Serializable data class RutaDto(val id: Long, val fecha: String, val estado: String, val puntos: List<PuntoRutaDto>)
@Serializable data class PuntoRutaDto(
    val id: Long,
    val razonSocial: String,
    val direccion: String,
    val zona: String,
    val materialHabitual: String,
    val secuencia: Int,
    val atendido: Boolean,
    val motivoOmission: String? = null,
)

@Serializable data class PuntoDto(val id: Long, val razonSocial: String, val direccion: String, val zona: String, val materialHabitual: String)
@Serializable data class PaginaDto<T>(
    val contenido: List<T>,
    val pagina: Int,
    val tamanio: Int,
    val totalElementos: Long,
    val totalPaginas: Int,
    val ultima: Boolean,
)

@Serializable data class PesajeDto(
    val id: Long,
    val rutaId: Long,
    val puntoId: Long,
    val fechaHora: String,
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val pesoNeto: Double,
    val observacion: String? = null,
    val estado: String,
    val motivoAnulacion: String? = null,
    val operario: String,
)

@Serializable data class PesajeRequestDto(val pesoBruto: Double, val tara: Double, val material: String, val observacion: String)
@Serializable data class MotivoRequestDto(val motivo: String)
@Serializable data class PuntoCrearRequestDto(val razonSocial: String, val direccion: String, val zona: String, val materialHabitual: String)
@Serializable data class RutaCrearRequestDto(val fecha: String, val operarioId: Long, val puntosIds: List<Long>)
@Serializable data class ErrorDto(
    val timestamp: String? = null,
    val status: Int? = null,
    val error: String? = null,
    val message: String? = null,
    val path: String? = null,
    val validationErrors: Map<String, String> = emptyMap(),
)

fun LoginResponseDto.toDomain(expiraEpochSeconds: Long): Sesion =
    Sesion(token, usuario, rol, privilegios, nombre, expiraEpochSeconds)

fun UsuarioActualDto.toDomain(token: String, expiraEpochSeconds: Long): Sesion =
    Sesion(token, usuario, rol, privilegios, nombre, expiraEpochSeconds)

fun PuntoRutaDto.toDomain() = PuntoAcopio(id, razonSocial, direccion, zona, materialHabitual, secuencia, atendido, motivoOmission)
fun PuntoDto.toDomain() = PuntoAcopio(id, razonSocial, direccion, zona, materialHabitual, 0, false, null)
fun RutaDto.toDomain(ultimaSincronizacion: String? = null) = RutaRecojo(id, fecha, estado, puntos.map(PuntoRutaDto::toDomain), ultimaSincronizacion)
fun PaginaDto<PuntoDto>.toPaginaPuntosDomain() = Pagina(contenido.map(PuntoDto::toDomain), pagina, tamanio, totalElementos, totalPaginas, ultima)
fun PaginaDto<PesajeDto>.toPaginaPesajesDomain() = Pagina(contenido.map(PesajeDto::toDomain), pagina, tamanio, totalElementos, totalPaginas, ultima)
fun PesajeDto.toDomain(pendiente: Boolean = false) =
    Pesaje(id, puntoId, fechaHora, material, pesoBruto, tara, pesoNeto, operario,
        if (estado == "ANULADO") EstadoPesaje.Anulado(motivoAnulacion.orEmpty()) else EstadoPesaje.Registrado,
        pendiente, rutaId, observacion.orEmpty(), motivoAnulacion)

