package pe.upeu.ecorecicla.presentation.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.upeu.ecorecicla.domain.model.*
import pe.upeu.ecorecicla.presentation.theme.EcoRutasTheme

@Composable
fun EcoApp(vm: EcoViewModel) {
    val state by vm.estado.collectAsState()
    val session = state.sesion
    var pantalla by remember { mutableStateOf("Ruta") }
    var usuario by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var zona by remember { mutableStateOf("") }
    var bruto by remember { mutableStateOf("") }
    var tara by remember { mutableStateOf("") }
    var material by remember { mutableStateOf("") }
    var observacion by remember { mutableStateOf("") }
    var cancelMotivo by remember { mutableStateOf("") }
    var omisionMotivo by remember { mutableStateOf("") }
    var razon by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var zonaCrear by remember { mutableStateOf("") }
    var materialHabitual by remember { mutableStateOf("") }
    var fechaRuta by remember { mutableStateOf(kotlinx.datetime.Clock.System.now().toString().take(10)) }
    var operarioId by remember { mutableStateOf("") }
    var puntosIds by remember { mutableStateOf("") }
    var pesajeActivo by remember { mutableStateOf<Pesaje?>(null) }
    EcoRutasTheme {
        Surface(Modifier.fillMaxSize()) {
            if (state.recuperando && session == null) {
                Box(Modifier.fillMaxSize().padding(24.dp)) { CircularProgressIndicator() }
            } else if (session == null) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("EcoRutas", style = MaterialTheme.typography.headlineLarge)
                    Text("Ingreso al sistema de recojo")
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(usuario, { usuario = it }, label = { Text("Usuario") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(clave, { clave = it }, label = { Text("Contraseña") }, modifier = Modifier.fillMaxWidth())
                    state.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { vm.login(usuario, clave) }, enabled = !state.recuperando, modifier = Modifier.fillMaxWidth()) {
                        if (state.recuperando) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Iniciar sesión")
                    }
                }
            } else {
                Scaffold(topBar = { TopAppBar(title = { Text("EcoRutas · ${session.usuario}") }) }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val tabs = buildList {
                                add("Ruta"); add("Puntos")
                                if ("PUNTO_GESTIONAR" in session.privilegios) add("Administrar")
                                add("Perfil")
                            }
                            tabs.forEach { item -> TextButton(onClick = { pantalla = item; if (item == "Puntos") vm.cargarPuntos() }) { Text(item) } }
                            TextButton(onClick = { vm.sincronizar() }, enabled = !state.sincronizando) { Text(if (state.sincronizando) "Sync…" else "Sincronizar") }
                        }
                        state.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp)) }
                        when (pantalla) {
                            "Ruta" -> RutaScreen(state.ruta, session, vm) { p -> vm.elegirPunto(p); pantalla = "Detalle" }
                            "Puntos" -> {
                                OutlinedTextField(zona, { zona = it }, label = { Text("Filtrar por zona") }, modifier = Modifier.fillMaxWidth())
                                Button(onClick = { vm.cargarPuntos(zona, 0) }) { Text("Buscar") }
                                PuntosScreen(state.puntos, { p -> vm.elegirPunto(p); pantalla = "Detalle" }, { vm.reintentar() })
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { vm.cargarPuntos(state.filtroZona, state.pagina - 1) }, enabled = state.pagina > 0) { Text("Anterior") }
                                    Text("Página ${state.pagina + 1}", modifier = Modifier.padding(top = 12.dp))
                                    val mas = (state.puntos as? UiState.Contenido)?.valor?.size == 20
                                    TextButton(onClick = { vm.cargarPuntos(state.filtroZona, state.pagina + 1) }, enabled = mas) { Text("Siguiente") }
                                }
                            }
                            "Detalle" -> {
                                Text("Pesajes · ${session.rol}", style = MaterialTheme.typography.titleLarge)
                                when (val p = state.pesajes) {
                                    UiState.Cargando -> CircularProgressIndicator()
                                    UiState.Vacio -> Text("Sin pesajes registrados.")
                                    is UiState.Error -> { Text(p.mensaje); if (p.reintentar) TextButton(onClick = { vm.reintentar() }) { Text("Reintentar") } }
                                    is UiState.Contenido -> LazyColumn(Modifier.weight(1f)) { items(p.valor) { PesajeRow(it) { pesajeActivo = it } } }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { vm.paginaPesajes(-1) }, enabled = state.paginaPesajes > 0) { Text("Pesajes anteriores") }
                                    val morePesajes = (state.pesajes as? UiState.Contenido)?.valor?.size == 20
                                    TextButton(onClick = { vm.paginaPesajes(1) }, enabled = morePesajes) { Text("Más pesajes") }
                                }
                                val capacidades = pe.upeu.ecorecicla.domain.usecase.capacidades(session)
                                val puedeRegistrar = capacidades.puedeRegistrarPesajes
                                if (puedeRegistrar) {
                                    OutlinedTextField(bruto, { bruto = it }, label = { Text("Peso bruto") }, supportingText = { state.erroresCampo["pesoBruto"]?.let { Text(it, color = MaterialTheme.colorScheme.error) } }, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(tara, { tara = it }, label = { Text("Tara") }, supportingText = { state.erroresCampo["tara"]?.let { Text(it, color = MaterialTheme.colorScheme.error) } }, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(material, { material = it }, label = { Text("Material") }, supportingText = { state.erroresCampo["material"]?.let { Text(it, color = MaterialTheme.colorScheme.error) } }, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(observacion, { observacion = it }, label = { Text("Observación") }, supportingText = { state.erroresCampo["observacion"]?.let { Text(it, color = MaterialTheme.colorScheme.error) } }, modifier = Modifier.fillMaxWidth())
                                    val brutoN = bruto.toDoubleOrNull(); val taraN = tara.toDoubleOrNull()
                                    val valida = brutoN != null && taraN != null && pe.upeu.ecorecicla.domain.usecase.validarPesaje(PesajeFormulario(brutoN, taraN, material, observacion)).isEmpty()
                                    if (bruto.isNotBlank() && brutoN == null) Text("Ingresa un peso bruto válido.", color = MaterialTheme.colorScheme.error)
                                    if (tara.isNotBlank() && taraN == null) Text("Ingresa una tara válida.", color = MaterialTheme.colorScheme.error)
                                    Button(onClick = { vm.registrar(PesajeFormulario(brutoN!!, taraN!!, material, observacion)) }, enabled = valida) { Text("Registrar pesaje") }
                                }
                                pesajeActivo?.let { p ->
                                    if (pe.upeu.ecorecicla.domain.usecase.puedeCorregirPesaje(session, p)) Button(onClick = {
                                        val b = bruto.toDoubleOrNull(); val t = tara.toDoubleOrNull()
                                        if (b != null && t != null) vm.corregir(p, PesajeFormulario(b, t, material.ifBlank { p.material }, observacion.ifBlank { p.observacion }))
                                    }) { Text("Corregir seleccionado") }
                                    if (capacidades.puedeAnularPesajes) {
                                        OutlinedTextField(cancelMotivo, { cancelMotivo = it }, label = { Text("Motivo de anulación") })
                                        Button(onClick = { if (cancelMotivo.isNotBlank()) vm.anular(p, cancelMotivo) }, enabled = cancelMotivo.isNotBlank() && p.estado == EstadoPesaje.Registrado) { Text("Anular seleccionado") }
                                    }
                                }
                                if (capacidades.puedeOmitirPunto) {
                                    OutlinedTextField(omisionMotivo, { omisionMotivo = it }, label = { Text("Motivo de omisión") })
                                    Button(onClick = { if (omisionMotivo.isNotBlank()) vm.omitirSeleccionado(omisionMotivo) }, enabled = omisionMotivo.isNotBlank()) { Text("Omitir punto") }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            "Administrar" -> {
                                Text("Crear punto de acopio", style = MaterialTheme.typography.titleLarge)
                                OutlinedTextField(razon, { razon = it }, label = { Text("Razón social") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(direccion, { direccion = it }, label = { Text("Dirección") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(zonaCrear, { zonaCrear = it }, label = { Text("Zona") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(materialHabitual, { materialHabitual = it }, label = { Text("Material habitual") }, modifier = Modifier.fillMaxWidth())
                                Button(onClick = { vm.crearPunto(PuntoCrear(razon, direccion, zonaCrear, materialHabitual)) }, enabled = listOf(razon, direccion, zonaCrear, materialHabitual).all { it.isNotBlank() }) { Text("Crear punto") }
                                Spacer(Modifier.height(16.dp))
                                Text("Crear ruta y asignación", style = MaterialTheme.typography.titleLarge)
                                OutlinedTextField(fechaRuta, { fechaRuta = it }, label = { Text("Fecha (AAAA-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(operarioId, { operarioId = it }, label = { Text("ID de operario") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(puntosIds, { puntosIds = it }, label = { Text("IDs de puntos, separados por coma") }, modifier = Modifier.fillMaxWidth())
                                val ids = puntosIds.split(',').mapNotNull { it.trim().toLongOrNull() }
                                Button(onClick = { vm.crearRuta(fechaRuta, operarioId.toLong(), ids) }, enabled = fechaRuta.length == 10 && operarioId.toLongOrNull() != null && ids.isNotEmpty()) { Text("Crear ruta") }
                            }
                            else -> PerfilScreen(session, vm)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun RutaScreen(state: UiState<RutaRecojo>, session: Sesion, vm: EcoViewModel, onSelect: (PuntoAcopio) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Ruta del día", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = { vm.refrescarRuta() }) { Text("Actualizar") }
    }
    when (state) {
        UiState.Cargando -> CircularProgressIndicator()
        UiState.Vacio -> Text("No hay ruta en caché. Toca Actualizar para consultar el servidor.")
        is UiState.Error -> { Text(state.mensaje); if (state.reintentar) TextButton(onClick = { vm.reintentar() }) { Text("Reintentar") } }
        is UiState.Contenido -> {
            Text("${state.valor.fecha} · ${state.valor.estado}")
            Text("Última sincronización: ${state.valor.ultimaSincronizacion ?: "Aún no sincronizada"}")
            LazyColumn(Modifier.heightIn(max = 400.dp)) { items(state.valor.puntos.sortedBy { it.secuencia }) { p ->
                ListItem(headlineContent = { Text("${p.secuencia}. ${p.razonSocial}") }, supportingContent = { Text("${p.direccion} · ${p.zona}${p.motivoOmission?.let { " · Omisión: $it" }.orEmpty()}") }, trailingContent = { Text(if (p.atendido) "Atendido" else "Pendiente") }, modifier = Modifier.clickable { onSelect(p) })
            } }
            if (pe.upeu.ecorecicla.domain.usecase.capacidades(session).puedeCerrarRuta) {
                Button(onClick = { vm.cerrarRuta() }) { Text("Cerrar ruta") }
            }
        }
    }
}

@Composable private fun PuntosScreen(state: UiState<List<PuntoAcopio>>, onSelect: (PuntoAcopio) -> Unit, onRetry: () -> Unit) {
    when (state) {
        UiState.Cargando -> CircularProgressIndicator()
        UiState.Vacio -> Text("No hay puntos locales. Revisa la conexión y busca de nuevo.")
        is UiState.Error -> { Text(state.mensaje); if (state.reintentar) TextButton(onClick = onRetry) { Text("Reintentar") } }
        is UiState.Contenido -> LazyColumn { items(state.valor) { p ->
            ListItem(headlineContent = { Text(p.razonSocial) }, supportingContent = { Text("${p.direccion} · ${p.zona} · ${p.materialHabitual}") }, modifier = Modifier.clickable { onSelect(p) })
        } }
    }
}

@Composable private fun PesajeRow(p: Pesaje, onClick: () -> Unit) {
    ListItem(headlineContent = { Text("${p.material} · neto ${p.pesoNeto} kg") }, supportingContent = { Text("${p.fechaHora} · ${p.operario}${if (p.pendiente) " · pendiente de sincronización" else ""}") }, modifier = Modifier.clickable(onClick = onClick))
}

@Composable private fun PerfilScreen(s: Sesion, vm: EcoViewModel) {
    Text("Perfil", style = MaterialTheme.typography.headlineSmall)
    Text("${s.nombre} (${s.usuario})")
    Text("Rol: ${s.rol}")
    Text("Privilegios: ${s.privilegios.sorted().joinToString().ifBlank { "Sin privilegios informados" }}")
    Spacer(Modifier.height(16.dp))
    Button(onClick = { vm.cerrarSesion() }) { Text("Cerrar sesión") }
}
