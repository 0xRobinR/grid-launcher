package app.gridlauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ProgressTest {
    @Test
    fun noonOnTheFirstOfJanuary() {
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.UK).apply {
            clear()
            set(2026, Calendar.JANUARY, 1, 12, 0, 0) // A Thursday; weeks start on Monday in the UK.
        }
        val p = progressOf(now).toMap()
        assertEquals(0.5f, p.getValue("Day"), 0.0001f)
        assertEquals(3.5f / 7f, p.getValue("Week"), 0.0001f)
        assertEquals(0.5f / 31f, p.getValue("Month"), 0.0001f)
        assertEquals(0.5f / 365f, p.getValue("Year"), 0.0001f)
    }
}
