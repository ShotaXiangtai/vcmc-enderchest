package jp.vcmc.enderchest.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayoutTest {
    private fun roles(layout: Layout, page: Int) = (0 until layout.size()).map { layout.role(page, it) }

    @Test
    fun `既定の 41 枠は 5 行で、最後の行の残りは使えない枠`() {
        val layout = Layout(41)
        assertFalse(layout.paged)
        assertEquals(5, layout.rows())
        val roles = roles(layout, 0)
        assertEquals((0 until 41).map { SlotRole.Storage(it) }, roles.take(41))
        assertEquals(List(4) { SlotRole.Locked }, roles.drop(41))
    }

    @Test
    fun `27 枠と 54 枠は行がちょうど埋まる`() {
        assertEquals(3, Layout(27).rows())
        assertTrue(roles(Layout(27), 0).all { it is SlotRole.Storage })
        val full = Layout(54)
        assertFalse(full.paged)
        assertEquals(6, full.rows())
        assertTrue(roles(full, 0).all { it is SlotRole.Storage })
    }

    @Test
    fun `54 枠を超えると 45 枠ずつのページに分ける`() {
        val layout = Layout(55)
        assertTrue(layout.paged)
        assertEquals(2, layout.pageCount)
        assertEquals(6, layout.rows())

        val first = roles(layout, 0)
        assertEquals((0 until 45).map { SlotRole.Storage(it) }, first.take(45))
        assertEquals(SlotRole.Filler, first[Layout.PREVIOUS])
        assertEquals(SlotRole.Info, first[Layout.INFO])
        assertEquals(SlotRole.Next, first[Layout.NEXT])

        val second = roles(layout, 1)
        assertEquals((45 until 55).map { SlotRole.Storage(it) }, second.take(10))
        assertEquals(List(35) { SlotRole.Locked }, second.subList(10, 45))
        assertEquals(SlotRole.Previous, second[Layout.PREVIOUS])
        assertEquals(SlotRole.Filler, second[Layout.NEXT])
    }

    @Test
    fun `どの容量でも、すべての枠がちょうど 1 回ずつ表示される`() {
        for (capacity in listOf(27, 41, 45, 53, 54, 55, 90, 91, 135, 136, 400)) {
            val layout = Layout(capacity)
            val shown = (0 until layout.pageCount).flatMap { page -> roles(layout, page) }
                .filterIsInstance<SlotRole.Storage>()
                .map { it.index }
            assertEquals((0 until capacity).toList(), shown, "容量 $capacity")
        }
    }

    @Test
    fun `ページの番号は範囲内に収める`() {
        val layout = Layout(135)
        assertEquals(3, layout.pageCount)
        assertEquals(0, layout.clampPage(-1))
        assertEquals(2, layout.clampPage(5))
    }
}
