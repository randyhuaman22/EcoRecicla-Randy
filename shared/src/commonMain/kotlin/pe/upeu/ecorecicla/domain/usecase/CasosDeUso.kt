package pe.upeu.ecorecicla.domain.usecase

import pe.upeu.ecorecicla.domain.model.*
import pe.upeu.ecorecicla.domain.repository.RepositorioEco
import pe.upeu.ecorecicla.domain.repository.RepositorioSincronizacion

data class CapacidadesUsuario(
    val puedeRegistrarPesajes: Boolean,
    val puedeCorregirPesajes: Boolean,
    val puedeAnularPesajes: Boolean,
    val puedeCerrarRuta: Boolean,
    val puedeOmitirPunto: Boolean,
    val puedeAdministrar: Boolean,
)

fun capacidades(sesion: Sesion): CapacidadesUsuario {
    val permisos = sesion.privilegios
    return CapacidadesUsuario(
        puedeRegistrarPesajes = "PESAJE_CREAR" in permisos,
        puedeCorregirPesajes = "PESAJE_EDITAR" in permisos,
        puedeAnularPesajes = "PESAJE_ANULAR" in permisos,
        puedeCerrarRuta = "RUTA_CERRAR" in permisos,
        puedeOmitirPunto = "RUTA_CERRAR" in permisos,
        puedeAdministrar = "PUNTO_GESTIONAR" in permisos,
    )
}

fun puedeCorregirPesaje(sesion: Sesion, pesaje: Pesaje): Boolean =
    "PESAJE_EDITAR" in sesion.privilegios &&
        (pesaje.operario == sesion.usuario || "PESAJE_EDITAR_TODOS" in sesion.privilegios)

fun validarPesaje(formulario: PesajeFormulario): Map<String, String> = buildMap {
    if (formulario.pesoBruto <= 0) put("pesoBruto", "El peso bruto debe ser mayor que cero.")
    if (formulario.tara < 0) put("tara", "La tara no puede ser negativa.")
    if (formulario.pesoBruto < formulario.tara) put("pesoBruto", "El peso bruto debe ser mayor o igual que la tara.")
    if (formulario.material.isBlank()) put("material", "Selecciona o escribe el material.")
}

class IniciarSesion(private val repo: RepositorioEco) {
    suspend operator fun invoke(usuario: String, contrasena: String) = repo.login(usuario.trim(), contrasena)
}

class RecuperarSesion(private val repo: RepositorioEco) {
    suspend operator fun invoke() = repo.restaurarSesion()
}

class CerrarSesion(private val repo: RepositorioEco) {
    suspend operator fun invoke() = repo.cerrarSesion()
}

class ActualizarRuta(private val repo: RepositorioEco) {
    suspend operator fun invoke(usuario: String, fecha: String) = repo.actualizarRuta(usuario, fecha)
}

class CargarPuntos(private val repo: RepositorioEco) {
    suspend operator fun invoke(usuario: String, zona: String?, pagina: Int, tamanio: Int) = repo.cargarPuntos(usuario, zona, pagina, tamanio)
}

class GuardarPesaje(private val repo: RepositorioEco) {
    suspend operator fun invoke(usuario: String, rutaId: Long, puntoId: Long, formulario: PesajeFormulario) = repo.registrarPesaje(usuario, rutaId, puntoId, formulario)
}

class Sincronizar(private val repo: RepositorioSincronizacion) {
    suspend operator fun invoke(usuario: String) = repo.sincronizar(usuario)
}

