package pe.upeu.ecorecicla

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import pe.upeu.ecorecicla.data.local.BaseLocalSqlDelight
import pe.upeu.ecorecicla.db.EcoReciclaDb
import pe.upeu.ecorecicla.domain.model.PesajeFormulario
import pe.upeu.ecorecicla.domain.model.TipoOperacion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaseLocalSqlDelightTest {
    @Test fun registroOfflineUsaIdNegativoYConservaOperacionEnSqlDelight() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EcoReciclaDb.Schema.create(driver)
        val local = BaseLocalSqlDelight(EcoReciclaDb(driver), Dispatchers.Unconfined)
        val p = local.guardarPesajePendiente("turno-1", 8, 12, PesajeFormulario(15.0, 1.0, "Cartón", "nota"))
        assertEquals(-1L, p.id)
        assertTrue(p.pendiente)
        local.crearPendienteParaPesaje("turno-1", p.id, TipoOperacion.CREAR_PESAJE, 8, 12, "{}")
        assertEquals(1, local.cantidadPendiente("turno-1"))
        assertEquals(0, local.cantidadPendiente("otro-turno"))
        driver.close()
    }
}
