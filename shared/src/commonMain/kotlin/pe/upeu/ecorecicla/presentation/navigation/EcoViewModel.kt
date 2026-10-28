package pe.upeu.ecorecicla.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pe.upeu.ecorecicla.data.repository.Sincronizador
import pe.upeu.ecorecicla.domain.model.*
import pe.upeu.ecorecicla.domain.repository.RepositorioEco
import pe.upeu.ecorecicla.domain.usecase.validarPesaje

sealed interface UiState<out T> {
    data object Cargando : UiState<Nothing>
    data class Contenido<T>(val valor: T) : UiState<T>
    data object Vacio : UiState<Nothing>
    data class Error(val mensaje: String, val reintentar: Boolean = false, val campos: Map<String, String> = emptyMap()) : UiState<Nothing>
}

data class EstadoAplicacion(
    val recuperando: Boolean = true,
    val sesion: Sesion? = null,
    val ruta: UiState<RutaRecojo> = UiState.Cargando,
    val pesajes: UiState<List<Pesaje>> = UiState.Vacio,
    val puntos: UiState<List<PuntoAcopio>> = UiState.Vacio,
    val pagina: Int = 0,
    val paginaPesajes: Int = 0,
    val filtroZona: String = "",
    val mensaje: String? = null,
    val sincronizando: Boolean = false,
    val resumenSync: ResumenSincronizacion? = null,
    val erroresCampo: Map<String, String> = emptyMap(),
)

class EcoViewModel(private val repo: RepositorioEco, private val sincronizador: Sincronizador) : ViewModel() {
    private val mutable = MutableStateFlow(EstadoAplicacion())
    val estado: StateFlow<EstadoAplicacion> = mutable.asStateFlow()
    private var routeJob: Job? = null
    private var pointJob: Job? = null
    private var pesajeJob: Job? = null
    private var puntoSeleccionado: PuntoAcopio? = null

    init { recuperar() }

    fun recuperar() = viewModelScope.launch {
        when (val r = repo.restaurarSesion()) {
            is Resultado.Exito -> { mutable.value = mutable.value.copy(recuperando = false, sesion = r.valor); r.valor?.let { cargarPrincipal(it) } }
            is Resultado.Fallo -> mutable.value = mutable.value.copy(recuperando = false, sesion = null, mensaje = r.error.mensajeUI())
        }
    }
    fun login(usuario: String, clave: String) = viewModelScope.launch {
        mutable.value = mutable.value.copy(recuperando = true, mensaje = null)
        when (val r = repo.login(usuario, clave)) {
            is Resultado.Exito -> { mutable.value = mutable.value.copy(recuperando = false, sesion = r.valor); cargarPrincipal(r.valor) }
            is Resultado.Fallo -> mutable.value = mutable.value.copy(recuperando = false, mensaje = r.error.mensajeUI())
        }
    }
    private suspend fun cargarPrincipal(sesion: Sesion) {
        val fecha = fechaHoy()
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            repo.observarRuta(sesion.usuario, fecha).collectLatest { ruta ->
                mutable.value = mutable.value.copy(ruta = ruta?.let { UiState.Contenido(it) } ?: UiState.Vacio)
            }
        }
        refrescarRuta()
    }
    fun refrescarRuta() = viewModelScope.launch {
        val sesion = mutable.value.sesion ?: return@launch
        if (mutable.value.ruta !is UiState.Contenido) mutable.value = mutable.value.copy(ruta = UiState.Cargando)
        when (val r = repo.actualizarRuta(sesion.usuario, fechaHoy())) {
            is Resultado.Exito -> mutable.value = mutable.value.copy(mensaje = null)
            is Resultado.Fallo -> fallo(r.error)
        }
    }
    fun cargarPuntos(zona: String = mutable.value.filtroZona, pagina: Int = 0) {
        pointJob?.cancel()
        pointJob = viewModelScope.launch {
        val sesion = mutable.value.sesion ?: return@launch
        mutable.value = mutable.value.copy(filtroZona = zona, pagina = pagina, puntos = UiState.Cargando)
        launch {
            repo.observarPuntos(sesion.usuario, zona, pagina, 20).collectLatest { rows ->
                mutable.value = mutable.value.copy(puntos = if (rows.isEmpty()) UiState.Vacio else UiState.Contenido(rows))
            }
        }
        when (val r = repo.cargarPuntos(sesion.usuario, zona, pagina, 20)) {
            is Resultado.Exito -> Unit
            is Resultado.Fallo -> fallo(r.error)
        }
        }
    }
    fun elegirPunto(punto: PuntoAcopio, pagina: Int = 0) {
        pesajeJob?.cancel()
        pesajeJob = viewModelScope.launch {
        puntoSeleccionado = punto
        val usuario = mutable.value.sesion?.usuario ?: return@launch
        mutable.value = mutable.value.copy(pesajes = UiState.Cargando, paginaPesajes = pagina)
        launch {
            repo.observarPesajes(usuario, punto.id).collectLatest { rows ->
                mutable.value = mutable.value.copy(pesajes = if (rows.isEmpty()) UiState.Vacio else UiState.Contenido(rows))
            }
        }
        when (val r = repo.cargarPesajes(usuario, punto.id, pagina, 20)) {
            is Resultado.Exito -> Unit
            is Resultado.Fallo -> fallo(r.error)
        }
        }
    }
    fun paginaPesajes(delta: Int) { puntoSeleccionado?.let { elegirPunto(it, (mutable.value.paginaPesajes + delta).coerceAtLeast(0)) } }
    fun registrar(formulario: PesajeFormulario) = viewModelScope.launch {
        val s = mutable.value.sesion ?: return@launch
        val punto = puntoSeleccionado ?: return@launch
        val ruta = (mutable.value.ruta as? UiState.Contenido)?.valor ?: return@launch
        val errores = validarPesaje(formulario)
        if (errores.isNotEmpty()) { mutable.value = mutable.value.copy(erroresCampo = errores); return@launch }
        mutable.value = mutable.value.copy(erroresCampo = emptyMap())
        when (val r = repo.registrarPesaje(s.usuario, ruta.id, punto.id, formulario)) {
            is Resultado.Exito -> { mutable.value = mutable.value.copy(mensaje = if (r.valor.pendiente) "Guardado en este dispositivo; pendiente de sincronización." else "Pesaje registrado."); elegirPunto(punto) }
            is Resultado.Fallo -> { if (r.error is ErrorApi.Validacion) mutable.value = mutable.value.copy(erroresCampo = r.error.campos); fallo(r.error) }
        }
    }
    fun corregir(pesaje: Pesaje, formulario: PesajeFormulario) = viewModelScope.launch {
        val s = mutable.value.sesion ?: return@launch
        val errores = validarPesaje(formulario)
        if (errores.isNotEmpty()) { mutable.value = mutable.value.copy(erroresCampo = errores); return@launch }
        when (val r = repo.corregirPesaje(s.usuario, pesaje, formulario)) {
            is Resultado.Exito -> { mutable.value = mutable.value.copy(mensaje = "Pesaje corregido."); puntoSeleccionado?.let { elegirPunto(it) } }
            is Resultado.Fallo -> { if (r.error is ErrorApi.Validacion) mutable.value = mutable.value.copy(erroresCampo = r.error.campos); fallo(r.error) }
        }
    }
    fun anular(pesaje: Pesaje, motivo: String) = viewModelScope.launch {
        val s = mutable.value.sesion ?: return@launch
        when (val r = repo.anularPesaje(s.usuario, pesaje, motivo)) {
            is Resultado.Exito -> { mutable.value = mutable.value.copy(mensaje = "Pesaje anulado."); puntoSeleccionado?.let { elegirPunto(it) } }
            is Resultado.Fallo -> fallo(r.error)
        }
    }
    fun omitir(punto: PuntoAcopio, motivo: String) = viewModelScope.launch {
        val s = mutable.value.sesion ?: return@launch
        val ruta = (mutable.value.ruta as? UiState.Contenido)?.valor ?: return@launch
        when (val r = repo.omitirPunto(s.usuario, ruta.id, punto.id, motivo)) {
            is Resultado.Exito -> { mutable.value = mutable.value.copy(mensaje = "Omisión registrada."); refrescarRuta() }
            is Resultado.Fallo -> fallo(r.error)
        }
    }
    fun omitirSeleccionado(motivo: String) { puntoSeleccionado?.let { omitir(it, motivo) } }
    fun cerrarRuta() = viewModelScope.launch {
        val s = mutable.value.sesion ?: return@launch
        val ruta = (mutable.value.ruta as? UiState.Contenido)?.valor ?: return@launch
        when (val r = repo.cerrarRuta(s.usuario, ruta.id)) {
            is Resultado.Exito -> { mutable.value = mutable.value.copy(mensaje = "Ruta cerrada."); refrescarRuta() }
            is Resultado.Fallo -> fallo(r.error)
        }
    }
    fun crearPunto(punto: PuntoCrear) = viewModelScope.launch {
        val usuario = mutable.value.sesion?.usuario ?: return@launch
        when (val r = repo.crearPunto(usuario, punto)) {
            is Resultado.Exito -> mutable.value = mutable.value.copy(mensaje = "Punto ${r.valor} creado.")
            is Resultado.Fallo -> fallo(r.error)
        }
    }
    fun crearRuta(fecha: String, operarioId: Long, puntosIds: List<Long>) = viewModelScope.launch {
        val usuario = mutable.value.sesion?.usuario ?: return@launch
        when (val r = repo.crearRuta(usuario, fecha, operarioId, puntosIds)) {
            is Resultado.Exito -> mutable.value = mutable.value.copy(mensaje = "Ruta ${r.valor.id} creada.")
            is Resultado.Fallo -> fallo(r.error)
        }
    }
    fun sincronizar() = viewModelScope.launch {
        val sesion = mutable.value.sesion ?: return@launch
        mutable.value = mutable.value.copy(sincronizando = true, mensaje = null)
        when (val r = sincronizador.sincronizar(sesion.usuario, fechaHoy())) {
            is Resultado.Exito -> { mutable.value = mutable.value.copy(sincronizando = false, resumenSync = r.valor, mensaje = "Sincronización: ${r.valor.enviados} enviados, ${r.valor.recibidos} recibidos, ${r.valor.pendientes} pendientes."); refrescarRuta() }
            is Resultado.Fallo -> { mutable.value = mutable.value.copy(sincronizando = false); fallo(r.error) }
        }
    }
    fun reintentar() {
        when {
            mutable.value.pesajes is UiState.Error && puntoSeleccionado != null -> puntoSeleccionado?.let { elegirPunto(it, mutable.value.paginaPesajes) }
            mutable.value.puntos is UiState.Error -> cargarPuntos(mutable.value.filtroZona, mutable.value.pagina)
            mutable.value.ruta is UiState.Error -> refrescarRuta()
        }
    }
    fun cerrarSesion() = viewModelScope.launch {
        val usuario = mutable.value.sesion?.usuario
        val pendiente = usuario?.let { repo.hayPendientes(it) } ?: 0
        repo.cerrarSesion()
        routeJob?.cancel(); routeJob = null
        pointJob?.cancel(); pointJob = null
        pesajeJob?.cancel(); pesajeJob = null
        mutable.value = EstadoAplicacion(recuperando = false, mensaje = if (pendiente > 0) "Sesión cerrada. Quedan $pendiente operaciones locales para el siguiente acceso de este usuario." else null)
    }
    private fun fallo(e: ErrorApi) {
        if (e is ErrorApi.NoAutenticado) viewModelScope.launch { repo.cerrarSesion(); mutable.value = EstadoAplicacion(recuperando = false, mensaje = e.mensajeUI()) }
        else {
            val current = mutable.value
            val retry = e is ErrorApi.Servidor || e is ErrorApi.TiempoAgotado || e == ErrorApi.SinConexion
            mutable.value = current.copy(
                mensaje = e.mensajeUI(),
                ruta = if (current.ruta == UiState.Cargando) UiState.Error(e.mensajeUI(), retry) else current.ruta,
                puntos = if (current.puntos == UiState.Cargando) UiState.Error(e.mensajeUI(), retry) else current.puntos,
                pesajes = if (current.pesajes == UiState.Cargando) UiState.Error(e.mensajeUI(), retry) else current.pesajes,
            )
        }
    }
    private fun fechaHoy() = kotlinx.datetime.Clock.System.now().toString().take(10)
}

private fun ErrorApi.mensajeUI() = when (this) {
    is ErrorApi.Validacion -> mensaje
    is ErrorApi.NoAutenticado -> mensaje
    is ErrorApi.SinPrivilegio -> mensaje
    is ErrorApi.NoEncontrado -> mensaje
    is ErrorApi.Conflicto -> mensaje
    is ErrorApi.Servidor -> mensaje
    ErrorApi.SinConexion -> "Sin conexión. Se conservan los datos y operaciones locales."
    ErrorApi.TiempoAgotado -> "El servidor tardó demasiado. Intenta de nuevo."
    is ErrorApi.Desconocido -> mensaje
}
