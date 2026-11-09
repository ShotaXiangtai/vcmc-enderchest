package jp.vcmc.enderchest

import org.bukkit.configuration.file.YamlConfiguration
import java.io.InputStreamReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SettingsTest {
    private fun settings(text: String): Pair<Settings, List<String>> {
        val warnings = mutableListOf<String>()
        return Settings.from(YamlConfiguration().apply { loadFromString(text) }, warnings::add) to warnings
    }

    @Test
    fun `同梱の config_yml は標準の 1_5 倍を切り上げた 41 枠`() {
        val stream = assertNotNull(javaClass.classLoader.getResourceAsStream("config.yml"))
        val config = InputStreamReader(stream, Charsets.UTF_8).use { YamlConfiguration.loadConfiguration(it) }
        val warnings = mutableListOf<String>()
        val settings = Settings.from(config, warnings::add)
        assertEquals(Settings(41, 135), settings)
        assertEquals(emptyList(), warnings)
    }

    @Test
    fun `書いていなければ既定値を使う`() {
        assertEquals(Settings(Settings.DEFAULT_SLOTS, Settings.MAX_SLOTS) to emptyList(), settings(""))
    }

    @Test
    fun `範囲外の値は直して知らせる`() {
        val (low, lowWarnings) = settings("default-slots: 10\nmax-slots: 5\n")
        assertEquals(Settings(27, 27), low)
        assertEquals(2, lowWarnings.size)

        val (high, highWarnings) = settings("default-slots: 200\nmax-slots: 100\n")
        assertEquals(Settings(100, 100), high)
        assertTrue(highWarnings.single().contains("default-slots"))
    }
}
