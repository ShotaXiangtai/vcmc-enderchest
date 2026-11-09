package jp.vcmc.enderchest.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SlotBufferTest {
    private val vanilla: List<String?> = List(Capacity.VANILLA) { if (it % 2 == 0) "v$it" else null }

    @Test
    fun `標準のエンダーチェストの中身はそのまま最初の 27 枠に入る`() {
        val buffer = SlotBuffer(vanilla, emptyList(), 41)
        assertEquals(41, buffer.size)
        assertEquals("v0", buffer[0])
        assertEquals(null, buffer[1])
        assertEquals(vanilla, buffer.vanilla())
        assertEquals(emptyList(), buffer.extra())
    }

    @Test
    fun `追加の枠は 27 枠目から入り、後ろの空きは保存しない`() {
        val buffer = SlotBuffer(vanilla, listOf("e0", null, "e2"), 41)
        assertEquals("e0", buffer[27])
        assertEquals("e2", buffer[29])
        buffer[40] = "last"
        assertEquals(listOf("e0", null, "e2") + List(10) { null } + "last", buffer.extra())
        buffer[40] = null
        assertEquals(listOf("e0", null, "e2"), buffer.extra())
    }

    @Test
    fun `容量を減らしても、容量より後ろの中身は消さずに保存し直す`() {
        val extra = List(20) { "e$it" }
        val buffer = SlotBuffer(vanilla, extra, 30)
        assertEquals(47, buffer.size)
        assertEquals(17, buffer.hiddenCount())
        buffer[27] = "changed"
        assertEquals(listOf("changed") + extra.drop(1), buffer.extra())
    }

    @Test
    fun `標準のエンダーチェストより小さい容量にはできない`() {
        assertFailsWith<IllegalArgumentException> { SlotBuffer(vanilla, emptyList(), 26) }
    }
}
