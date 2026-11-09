package jp.vcmc.enderchest

import java.io.File
import java.util.UUID
import java.util.logging.Logger
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapacityStoreTest {
    private val logger = Logger.getLogger("CapacityStoreTest")
    private val alice = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val bob = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private fun withFile(block: (File) -> Unit) {
        val dir = createTempDirectory("capacity").toFile()
        try {
            block(File(dir, "capacity.yml"))
        } finally {
            dir.deleteRecursively()
        }
    }

    /** 保存を別スレッドに渡さず、その場で行う。 */
    private fun store(file: File) = CapacityStore(file, logger) { it.run() }

    @Test
    fun `保存した追加の枠数を読み直せる`() = withFile { file ->
        val first = store(file).apply { load() }
        assertEquals(0, first.extra(alice))
        assertEquals("ok", first.update(alice) { (it + 9) to "ok" })
        first.update(bob) { -5 to Unit }

        val second = store(file).apply { load() }
        assertEquals(9, second.extra(alice))
        assertEquals(-5, second.extra(bob))
    }

    @Test
    fun `0 に戻したプレイヤーはファイルに残さない`() = withFile { file ->
        val store = store(file).apply { load() }
        store.update(alice) { 9 to Unit }
        store.update(alice) { 0 to Unit }
        assertFalse(file.readText().contains(alice.toString()))
    }

    @Test
    fun `読めないファイルは上書きせず、変更もしない`() = withFile { file ->
        val broken = "players: [this is: not valid\n"
        file.writeText(broken)
        val store = store(file).apply { load() }
        assertFalse(store.writable)
        assertEquals(0, store.extra(alice))
        assertFailsWith<IllegalStateException> { store.update(alice) { 9 to Unit } }
        store.save()
        assertEquals(broken, file.readText())
    }

    @Test
    fun `読めない行だけを飛ばす`() = withFile { file ->
        file.writeText("players:\n  $alice: 9\n  not-a-uuid: 3\n  $bob: many\n")
        val store = store(file).apply { load() }
        assertTrue(store.writable)
        assertEquals(9, store.extra(alice))
        assertEquals(0, store.extra(bob))
    }
}
