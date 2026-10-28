package pe.upeu.ecorecicla.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import pe.upeu.ecorecicla.db.EcoReciclaDb
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.domain.repository.SesionSegura

class AlmacenSeguroAndroid(context: Context) : SesionSegura {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ecorutas_secure_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    override suspend fun guardar(sesion: Sesion) {
        prefs.edit().putString("token", sesion.token).putString("usuario", sesion.usuario).putString("rol", sesion.rol)
            .putString("nombre", sesion.nombre).putString("privilegios", sesion.privilegios.joinToString("\u001f"))
            .putLong("expira", sesion.expiraEpochSeconds).apply()
    }
    override suspend fun leer(): Sesion? {
        val token = prefs.getString("token", null)?.takeIf(String::isNotBlank) ?: return null
        return Sesion(token, prefs.getString("usuario", "").orEmpty(), prefs.getString("rol", "").orEmpty(),
            prefs.getString("privilegios", "").orEmpty().split("\u001f").filter(String::isNotBlank).toSet(),
            prefs.getString("nombre", "").orEmpty(), prefs.getLong("expira", 0L))
    }
    override suspend fun borrar() { prefs.edit().clear().apply() }
}

fun crearBaseLocal(context: Context) = EcoReciclaDb(AndroidSqliteDriver(EcoReciclaDb.Schema, context, "ecorutas.db"))
