package pe.upeu.ecorecicla.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import pe.upeu.ecorecicla.data.local.*
import pe.upeu.ecorecicla.data.remote.*
import pe.upeu.ecorecicla.data.repository.*
import pe.upeu.ecorecicla.domain.repository.RepositorioEco
import pe.upeu.ecorecicla.domain.repository.SesionSegura
import pe.upeu.ecorecicla.presentation.navigation.EcoApp
import pe.upeu.ecorecicla.presentation.navigation.EcoViewModel

class MainActivity : ComponentActivity() {
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val graph: Module by lazy {
        module {
            single<SesionSegura> { AlmacenSeguroAndroid(androidContext()) }
            single { ApiConfig(BuildConfig.API_BASE_URL) }
            single { OkHttp.create() }
            single { crearClienteHttp(get(), get(), get(), BuildConfig.DEBUG) }
            single { EcoApi(get()) }
            single { crearBaseLocal(androidContext()) }
            single<BaseLocal> { BaseLocalSqlDelight(get()) }
            single<RepositorioEco> { RepositorioEcoImpl(get(), get(), get()) }
            single { Sincronizador(get(), get()) }
            single { EcoViewModel(get(), get()) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (org.koin.core.context.GlobalContext.getOrNull() == null) startKoin { androidContext(this@MainActivity); modules(graph) }
        setContent { EcoApp(org.koin.java.KoinJavaComponent.get(EcoViewModel::class.java)) }
        observarConectividad()
    }

    private fun observarConectividad() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var conectado = false
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (conectado) return
                conectado = true
                val model: EcoViewModel = org.koin.java.KoinJavaComponent.get(EcoViewModel::class.java)
                model.sincronizar()
            }
            override fun onLost(network: Network) { conectado = false }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) onAvailable(network)
            }
        }
        networkCallback = callback
        cm.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback)
    }

    override fun onDestroy() {
        networkCallback?.let { (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it) }
        super.onDestroy()
    }
}
