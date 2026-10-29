package pe.upeu.ecorecicla.presentation.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.datetime.toLocalDateTime
import pe.upeu.ecorecicla.domain.model.*
import pe.upeu.ecorecicla.domain.usecase.capacidades
import pe.upeu.ecorecicla.domain.usecase.puedeCorregirPesaje
import pe.upeu.ecorecicla.domain.usecase.validarPesaje
import pe.upeu.ecorecicla.presentation.theme.EcoRutasTheme

private val inset = 18.dp

@Composable
fun EcoApp(vm: EcoViewModel) {
    val state by vm.estado.collectAsState()
    val sesion = state.sesion
    var tab by remember { mutableStateOf("Ruta") }
    var punto by remember { mutableStateOf<PuntoAcopio?>(null) }
    var usuario by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var busqueda by remember { mutableStateOf("") }
    var zona by remember { mutableStateOf("") }
    var formulario by remember { mutableStateOf(false) }
    var seleccionado by remember { mutableStateOf<Pesaje?>(null) }
    var bruto by remember { mutableStateOf("") }
    var tara by remember { mutableStateOf("") }
    var material by remember { mutableStateOf("") }
    var observacion by remember { mutableStateOf("") }
    var motivoAnulacion by remember { mutableStateOf("") }
    var motivoOmision by remember { mutableStateOf("") }
    var pedirCierre by remember { mutableStateOf(false) }
    var razon by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var zonaCrear by remember { mutableStateOf("") }
    var materialHabitual by remember { mutableStateOf("") }
    var fechaRuta by remember { mutableStateOf(kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString()) }
    var operarioId by remember { mutableStateOf("") }
    var puntosIds by remember { mutableStateOf("") }

    LaunchedEffect(sesion?.usuario) {
        tab = "Ruta"; punto = null; formulario = false; seleccionado = null
        if (sesion != null) clave = ""
    }
    LaunchedEffect(state.confirmaciones) {
        if (state.confirmaciones > 0) {
            seleccionado = null; formulario = false; bruto = ""; tara = ""
            observacion = ""; motivoAnulacion = ""
        }
    }

    fun abrirPunto(p: PuntoAcopio) {
        punto = p; formulario = false; seleccionado = null
        bruto = ""; tara = ""; material = p.materialHabitual; observacion = ""
        motivoAnulacion = ""; motivoOmision = ""
        vm.elegirPunto(p)
    }
    fun volver() { punto = null; formulario = false; seleccionado = null }

    EcoRutasTheme {
        if (sesion == null) {
            if (state.recuperando) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp), verticalArrangement = Arrangement.Center) {
                    Spacer(Modifier.height(48.dp))
                    Text("ECORECICLA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("Iniciar sesión", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Coloca tu usuario y contraseña para continuar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(32.dp))
                    ElevatedCard { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedTextField(usuario, { usuario = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(clave, { clave = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                        state.mensaje?.let { Aviso(it, true) }
                        Button(onClick = { vm.login(usuario.trim(), clave) }, enabled = usuario.isNotBlank() && clave.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Iniciar sesión") }
                    } }
                }
            }
        } else {
            val tabs = buildList { add("Ruta"); add("Puntos"); if (capacidades(sesion).puedeAdministrar) add("Administrar"); add("Perfil") }
            Scaffold(
                topBar = { TopAppBar(
                    title = { Column { Text(if (punto == null) "EcoRutas" else "Detalle del punto", fontWeight = FontWeight.Bold); Text(if (punto == null) sesion.nombre else punto!!.razonSocial, style = MaterialTheme.typography.labelMedium) } },
                    navigationIcon = { if (punto != null) TextButton(onClick = { volver() }) { Text("Volver") } },
                    actions = { if (punto == null) TextButton(onClick = { vm.sincronizar() }, enabled = !state.sincronizando) { Text(if (state.sincronizando) "Sincronizando…" else "Sincronizar") } }
                ) },
                bottomBar = { if (punto == null) NavigationBar {
                    tabs.forEach { destino -> NavigationBarItem(
                        selected = tab == destino,
                        onClick = { tab = destino; if (destino == "Puntos") vm.cargarPuntos(zona, 0) },
                        icon = { Text(when (destino) { "Ruta" -> "◉"; "Puntos" -> "▦"; "Administrar" -> "＋"; else -> "●" }) },
                        label = { Text(destino) }
                    ) }
                } }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (state.sincronizando) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.mensaje?.let { Box(Modifier.padding(horizontal = inset, vertical = 5.dp)) { Aviso(it, false) } }
                    if (punto != null) {
                        DetalleScreen(state, sesion, punto!!, vm, formulario, { formulario = it }, seleccionado, { p ->
                            seleccionado = p; formulario = false; bruto = p.pesoBruto.toString(); tara = p.tara.toString()
                            material = p.material; observacion = p.observacion; motivoAnulacion = ""
                        }, bruto, { bruto = it }, tara, { tara = it }, material, { material = it }, observacion, { observacion = it },
                            motivoAnulacion, { motivoAnulacion = it }, motivoOmision, { motivoOmision = it },
                            onSaved = { seleccionado = null; formulario = false; bruto = ""; tara = ""; observacion = ""; motivoAnulacion = "" })
                    } else when (tab) {
                        "Ruta" -> RutaScreen(state.ruta, sesion, vm, ::abrirPunto, { pedirCierre = true })
                        "Puntos" -> PuntosScreen(state, vm, busqueda, { busqueda = it }, zona, { zona = it }, ::abrirPunto)
                        "Administrar" -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(inset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item { Cabecera("Administrar", "Crear puntos y asignar rutas a operarios") }
                            item { Seccion("Nuevo punto de acopio") { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Campo(razon, { razon = it }, "Razón social"); Campo(direccion, { direccion = it }, "Dirección")
                                Campo(zonaCrear, { zonaCrear = it }, "Zona"); Campo(materialHabitual, { materialHabitual = it }, "Material habitual")
                                Button(onClick = { vm.crearPunto(PuntoCrear(razon.trim(), direccion.trim(), zonaCrear.trim(), materialHabitual.trim())); razon = ""; direccion = ""; zonaCrear = ""; materialHabitual = "" }, enabled = listOf(razon, direccion, zonaCrear, materialHabitual).all { it.isNotBlank() }, modifier = Modifier.fillMaxWidth()) { Text("Crear punto") }
                            } } }
                            item { Seccion("Nueva ruta") { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Campo(fechaRuta, { fechaRuta = it }, "Fecha (AAAA-MM-DD)"); Campo(operarioId, { operarioId = it }, "ID del operario")
                                Campo(puntosIds, { puntosIds = it }, "IDs de puntos separados por comas")
                                val ids = puntosIds.split(',').map { it.trim().toLongOrNull() }
                                Button(onClick = { vm.crearRuta(fechaRuta, operarioId.toLong(), ids.filterNotNull()); operarioId = ""; puntosIds = "" }, enabled = fechaRuta.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && operarioId.toLongOrNull() != null && ids.isNotEmpty() && ids.all { it != null }, modifier = Modifier.fillMaxWidth()) { Text("Crear y asignar ruta") }
                            } } }
                        }
                        else -> PerfilScreen(sesion, state, vm)
                    }
                }
            }
            if (pedirCierre) AlertDialog(onDismissRequest = { pedirCierre = false }, title = { Text("Cerrar ruta") }, text = { Text("Confirma que todos los puntos tienen un pesaje o un motivo de omisión. El servidor validará el cierre.") }, confirmButton = { TextButton(onClick = { pedirCierre = false; vm.cerrarRuta() }) { Text("Cerrar ruta") } }, dismissButton = { TextButton(onClick = { pedirCierre = false }) { Text("Cancelar") } })
        }
    }
}

@Composable private fun Cabecera(titulo: String, descripcion: String) {
    Column { Text(titulo, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(descripcion, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            contenido()
        }
    }
}

@Composable private fun Campo(valor: String, cambiar: (String) -> Unit, etiqueta: String, error: String? = null) {
    OutlinedTextField(valor, cambiar, label = { Text(etiqueta) }, isError = error != null,
        supportingText = if (error == null) null else { { Text(error) } }, modifier = Modifier.fillMaxWidth(), singleLine = etiqueta != "Observación")
}

@Composable private fun Aviso(texto: String, error: Boolean) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
        Text(texto, Modifier.fillMaxWidth().padding(12.dp), color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable private fun EstadoLista(texto: String, reintentar: (() -> Unit)? = null) {
    Seccion("Estado de la consulta") { Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant); if (reintentar != null) OutlinedButton(onClick = reintentar) { Text("Reintentar") } }
}

@Composable private fun RutaScreen(estado: UiState<RutaRecojo>, sesion: Sesion, vm: EcoViewModel, abrir: (PuntoAcopio) -> Unit, cerrar: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(inset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Cabecera("Ruta del día", "Tus visitas, en el orden planificado") }
        item { OutlinedButton(onClick = { vm.refrescarRuta() }, modifier = Modifier.fillMaxWidth()) { Text("Actualizar ruta") } }
        when (estado) {
            UiState.Cargando -> item { CircularProgressIndicator() }
            UiState.Vacio -> item { EstadoLista("Aún no hay una ruta guardada para hoy. Conéctate y toca Actualizar ruta.") { vm.refrescarRuta() } }
            is UiState.Error -> item { EstadoLista(estado.mensaje, if (estado.reintentar) ({ vm.refrescarRuta() }) else null) }
            is UiState.Contenido -> {
                val ruta = estado.valor
                item { Seccion("${ruta.fecha}  ·  ${ruta.estado}") {
                    val atendidos = ruta.puntos.count { it.atendido }
                    Text("$atendidos de ${ruta.puntos.size} puntos atendidos", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    LinearProgressIndicator(progress = { if (ruta.puntos.isEmpty()) 0f else atendidos.toFloat() / ruta.puntos.size }, modifier = Modifier.fillMaxWidth())
                    Text("Última sincronización: ${ruta.ultimaSincronizacion ?: "Aún no sincronizada"}", style = MaterialTheme.typography.bodySmall)
                } }
                items(ruta.puntos.sortedBy { it.secuencia }, key = { it.id }) { p -> PuntoTarjeta(p, abrir) }
                if (capacidades(sesion).puedeCerrarRuta && ruta.estado.equals("ABIERTA", true)) item {
                    OutlinedButton(onClick = cerrar, modifier = Modifier.fillMaxWidth()) { Text("Cerrar ruta") }
                }
            }
        }
    }
}

@Composable private fun PuntoTarjeta(p: PuntoAcopio, abrir: (PuntoAcopio) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable { abrir(p) }, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("${if (p.secuencia > 0) "${p.secuencia}. " else ""}${p.razonSocial}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(p.direccion, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${p.zona} · ${p.materialHabitual}", style = MaterialTheme.typography.bodySmall)
                Text(if (p.atendido) "Atendido" else if (p.motivoOmission != null) "Omitido" else "Pendiente", color = if (p.atendido) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            }
            p.motivoOmission?.let { Text("Motivo: $it", style = MaterialTheme.typography.bodySmall) }
            Text("Ver detalle y pesajes  →", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable private fun PuntosScreen(state: EstadoAplicacion, vm: EcoViewModel, busqueda: String, cambiarBusqueda: (String) -> Unit, zona: String, cambiarZona: (String) -> Unit, abrir: (PuntoAcopio) -> Unit) {
    val puntos = (state.puntos as? UiState.Contenido)?.valor.orEmpty()
    val zonas = ((state.ruta as? UiState.Contenido)?.valor?.puntos.orEmpty() + puntos).map { it.zona }.filter { it.isNotBlank() }.distinct().sorted()
    val visibles = puntos.filter { busqueda.isBlank() || listOf(it.razonSocial, it.direccion, it.materialHabitual).any { valor -> valor.contains(busqueda.trim(), ignoreCase = true) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(inset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Cabecera("Puntos de acopio", "Consulta el padrón y los pesajes de cada punto") }
        item { Seccion("Filtrar por zona") {
            Text("Selecciona una zona o escríbela. El filtro se aplica al servidor y a los puntos guardados.", style = MaterialTheme.typography.bodySmall)
            Campo(zona, cambiarZona, "Zona (ejemplo: Centro)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.cargarPuntos(zona.trim(), 0) }) { Text("Aplicar zona") }
                TextButton(onClick = { cambiarZona(""); cambiarBusqueda(""); vm.cargarPuntos("", 0) }) { Text("Ver todos") }
            }
            if (zonas.isNotEmpty()) {
                Text("Zonas disponibles:", style = MaterialTheme.typography.labelMedium)
                zonas.take(4).forEach { nombre ->
                    SuggestionChip(onClick = { cambiarZona(nombre); cambiarBusqueda(""); vm.cargarPuntos(nombre, 0) }, label = { Text(nombre) })
                }
            }
        } }
        item { Campo(busqueda, cambiarBusqueda, "Buscar por empresa, dirección o material") }
        when (val resultado = state.puntos) {
            UiState.Cargando -> item { CircularProgressIndicator() }
            UiState.Vacio -> item { EstadoLista(if (zona.isBlank()) "No hay puntos guardados. Conéctate y actualiza la lista." else "No hay puntos para la zona «$zona». Prueba otra zona o toca Ver todos.") { vm.cargarPuntos(zona, 0) } }
            is UiState.Error -> item { EstadoLista(resultado.mensaje, if (resultado.reintentar) ({ vm.cargarPuntos(zona, state.pagina) }) else null) }
            is UiState.Contenido -> {
                if (visibles.isEmpty()) item { EstadoLista("No hay coincidencias entre los ${puntos.size} puntos cargados. Prueba otro nombre o dirección.") }
                else items(visibles, key = { it.id }) { p -> PuntoTarjeta(p, abrir) }
            }
        }
        if (state.puntos is UiState.Contenido && busqueda.isBlank()) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.cargarPuntos(state.filtroZona, state.pagina - 1) }, enabled = state.pagina > 0) { Text("Anterior") }
                Text("Página ${state.pagina + 1}")
                TextButton(onClick = { vm.cargarPuntos(state.filtroZona, state.pagina + 1) }, enabled = puntos.size >= 20) { Text("Siguiente") }
            }
        }
    }
}

@Composable private fun DetalleScreen(state: EstadoAplicacion, sesion: Sesion, punto: PuntoAcopio, vm: EcoViewModel,
    formulario: Boolean, cambiarFormulario: (Boolean) -> Unit, seleccionado: Pesaje?, elegir: (Pesaje) -> Unit,
    bruto: String, cambiarBruto: (String) -> Unit, tara: String, cambiarTara: (String) -> Unit,
    material: String, cambiarMaterial: (String) -> Unit, observacion: String, cambiarObservacion: (String) -> Unit,
    motivoAnulacion: String, cambiarMotivoAnulacion: (String) -> Unit,
    motivoOmision: String, cambiarMotivoOmision: (String) -> Unit, onSaved: () -> Unit) {
    val capacidades = capacidades(sesion)
    val pesajes = (state.pesajes as? UiState.Contenido)?.valor.orEmpty()
    val brutoN = bruto.replace(',', '.').toDoubleOrNull()
    val taraN = tara.replace(',', '.').toDoubleOrNull()
    val errores = if (brutoN != null && taraN != null) validarPesaje(PesajeFormulario(brutoN, taraN, material, observacion)) else emptyMap()
    val valido = brutoN != null && taraN != null && errores.isEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(inset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Cabecera(punto.razonSocial, "${punto.direccion}  ·  ${punto.zona}") }
        item { Seccion("Información del punto") {
            Text("Material habitual: ${punto.materialHabitual}")
            Text("Estado: ${if (punto.atendido) "Atendido" else if (punto.motivoOmission != null) "Omitido" else "Pendiente"}")
            punto.motivoOmission?.let { Text("Omisión: $it") }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Historial de pesajes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = { vm.elegirPunto(punto, state.paginaPesajes) }) { Text("Actualizar") }
        } }
        when (val resultado = state.pesajes) {
            UiState.Cargando -> item { CircularProgressIndicator() }
            UiState.Vacio -> item { EstadoLista("Todavía no hay pesajes guardados para este punto. Si estás sin conexión, puedes registrar uno y sincronizar después.") }
            is UiState.Error -> item { EstadoLista(resultado.mensaje, if (resultado.reintentar) ({ vm.elegirPunto(punto) }) else null) }
            is UiState.Contenido -> items(resultado.valor, key = { it.id }) { p ->
                ElevatedCard(Modifier.fillMaxWidth().clickable { elegir(p) }, shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = if (seleccionado?.id == p.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.material, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${p.pesoNeto} kg netos", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Text("Bruto ${p.pesoBruto} kg  ·  Tara ${p.tara} kg")
                        Text("${p.fechaHora}  ·  ${p.operario}", style = MaterialTheme.typography.bodySmall)
                        if (p.pendiente) Text("● Pendiente de sincronización", color = MaterialTheme.colorScheme.tertiary)
                        if (p.estado is EstadoPesaje.Anulado) Text("Anulado: ${p.estado.motivo}", color = MaterialTheme.colorScheme.error)
                        p.observacion.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text(if (seleccionado?.id == p.id) "Seleccionado para revisar" else "Toca para ver acciones", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (pesajes.isNotEmpty()) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { vm.paginaPesajes(-1) }, enabled = state.paginaPesajes > 0) { Text("Anteriores") }
            Text("Página ${state.paginaPesajes + 1}", modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { vm.paginaPesajes(1) }, enabled = pesajes.size >= 20) { Text("Más pesajes") }
        } }
        if (capacidades.puedeRegistrarPesajes) item {
            Button(onClick = { onSaved(); cambiarMaterial(punto.materialHabitual); cambiarFormulario(true) }, modifier = Modifier.fillMaxWidth()) { Text("＋ Registrar un nuevo pesaje") }
        }
        if (seleccionado != null && seleccionado.estado == EstadoPesaje.Registrado) {
            if (puedeCorregirPesaje(sesion, seleccionado)) item { OutlinedButton(onClick = { cambiarFormulario(true) }, modifier = Modifier.fillMaxWidth()) { Text("Corregir pesaje seleccionado") } }
            if (capacidades.puedeAnularPesajes && !seleccionado.pendiente && seleccionado.id > 0) item { Seccion("Anular pesaje seleccionado") {
                Text("${seleccionado.material} · ${seleccionado.pesoNeto} kg netos")
                Campo(motivoAnulacion, cambiarMotivoAnulacion, "Motivo de anulación")
                Button(onClick = { vm.anular(seleccionado, motivoAnulacion.trim()) }, enabled = motivoAnulacion.isNotBlank() && !state.operando, modifier = Modifier.fillMaxWidth()) { Text("Confirmar anulación") }
            } }
        }
        if (formulario && (seleccionado == null && capacidades.puedeRegistrarPesajes || seleccionado != null && puedeCorregirPesaje(sesion, seleccionado))) item {
            Seccion(if (seleccionado == null) "Registrar pesaje" else "Corregir pesaje") {
                Text("Ingresa los kilos. El neto se calcula restando la tara al peso bruto.", style = MaterialTheme.typography.bodySmall)
                Campo(bruto, cambiarBruto, "Peso bruto (kg)", state.erroresCampo["pesoBruto"] ?: if (bruto.isNotBlank() && brutoN == null) "Ingresa un número válido" else errores["pesoBruto"])
                Campo(tara, cambiarTara, "Tara (kg)", state.erroresCampo["tara"] ?: if (tara.isNotBlank() && taraN == null) "Ingresa un número válido" else errores["tara"])
                Campo(material, cambiarMaterial, "Material", state.erroresCampo["material"] ?: errores["material"])
                Campo(observacion, cambiarObservacion, "Observación", state.erroresCampo["observacion"])
                if (valido) Text("Peso neto: ${brutoN!! - taraN!!} kg", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Button(onClick = {
                    val datos = PesajeFormulario(brutoN!!, taraN!!, material.trim(), observacion.trim())
                    if (seleccionado == null) vm.registrar(datos) else vm.corregir(seleccionado, datos)
                }, enabled = valido && !state.operando, modifier = Modifier.fillMaxWidth()) { Text(if (state.operando) "Guardando…" else if (seleccionado == null) "Guardar pesaje" else "Guardar corrección") }
                TextButton(onClick = { cambiarFormulario(false) }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
            }
        }
        if (capacidades.puedeOmitirPunto && !punto.atendido && punto.motivoOmission == null) item { Seccion("Punto sin recojo") {
            Text("Usa esta opción únicamente cuando no fue posible recoger material.", style = MaterialTheme.typography.bodySmall)
            Campo(motivoOmision, cambiarMotivoOmision, "Motivo de omisión")
            OutlinedButton(onClick = { vm.omitirSeleccionado(motivoOmision.trim()); cambiarMotivoOmision("") }, enabled = motivoOmision.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Registrar omisión") }
        } }
    }
}

@Composable private fun PerfilScreen(s: Sesion, state: EstadoAplicacion, vm: EcoViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(inset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Cabecera("Mi perfil", "Turno activo en este dispositivo") }
        item { Seccion(s.nombre) { Text("Usuario: ${s.usuario}"); Text("Rol: ${s.rol}") } }
        item { Seccion("Permisos de mi cuenta") {
            s.privilegios.sorted().forEach { Text("• ${it.replace('_', ' ').lowercase().replaceFirstChar { c -> c.uppercase() }}") }
        } }
        item { Seccion("Sincronización") {
            if (state.sincronizando) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.resumenSync?.let { Text("Enviados: ${it.enviados} · Recibidos: ${it.recibidos} · Pendientes: ${it.pendientes}"); if (it.errores.isNotEmpty()) Text("Operaciones con error: ${it.errores.size}", color = MaterialTheme.colorScheme.error) }
            Button(onClick = { vm.sincronizar() }, enabled = !state.sincronizando, modifier = Modifier.fillMaxWidth()) { Text(if (state.sincronizando) "Sincronizando…" else "Sincronizar ahora") }
        } }
        item { OutlinedButton(onClick = { vm.cerrarSesion() }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)) { Text("Cerrar sesión", color = MaterialTheme.colorScheme.error) } }
    }
}
