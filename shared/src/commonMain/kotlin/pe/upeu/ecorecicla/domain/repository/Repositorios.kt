package pe.upeu.ecorecicla.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.ecorecicla.domain.model.*

interface AlmacenSeguro {
    suspend fun guardar(sesion: Sesion)
    suspend fun leer(): Sesion?
    suspend fun borrar()
}

typealias SesionSegura = AlmacenSeguro

interface RepositorioEco {
    suspend fun login(usuario: String, contrasena: String): Resultado<Sesion>
    suspend fun usuarioActual(): Resultado<Sesion>
    fun observarRuta(usuario: String, fecha: String): Flow<RutaRecojo?>
    suspend fun actualizarRuta(usuario: String, fecha: String): Resultado<RutaRecojo>
    fun observarPuntos(usuario: String, zona: String, pagina: Int, tamanio: Int): Flow<List<PuntoAcopio>>
    suspend fun cargarPuntos(usuario: String, zona: String?, pagina: Int, tamanio: Int): Resultado<Int>
    fun observarPesajes(usuario: String, puntoId: Long): Flow<List<Pesaje>>
    suspend fun cargarPesajes(usuario: String, puntoId: Long, pagina: Int, tamanio: Int): Resultado<Int>
    suspend fun registrarPesaje(usuario: String, rutaId: Long, puntoId: Long, formulario: PesajeFormulario): Resultado<Pesaje>
    suspend fun corregirPesaje(usuario: String, pesaje: Pesaje, formulario: PesajeFormulario): Resultado<Pesaje>
    suspend fun anularPesaje(usuario: String, pesaje: Pesaje, motivo: String): Resultado<Unit>
    suspend fun omitirPunto(usuario: String, rutaId: Long, puntoId: Long, motivo: String): Resultado<Unit>
    suspend fun cerrarRuta(usuario: String, rutaId: Long): Resultado<Unit>
    suspend fun crearPunto(usuario: String, punto: PuntoCrear): Resultado<Long>
    suspend fun crearRuta(usuario: String, fecha: String, operarioId: Long, puntosIds: List<Long>): Resultado<RutaRecojo>
    suspend fun guardarSesion(sesion: Sesion)
    suspend fun restaurarSesion(): Resultado<Sesion?>
    suspend fun cerrarSesion()
    suspend fun hayPendientes(usuario: String): Int
}

interface RepositorioSincronizacion {
    suspend fun sincronizar(usuario: String): Resultado<ResumenSincronizacion>
    suspend fun pendientes(usuario: String): List<OperacionPendiente>
}

