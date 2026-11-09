package jp.vcmc.enderchest.logic

import kotlin.test.Test
import kotlin.test.assertEquals

class CapacityTest {
    @Test
    fun `既定の容量に追加分を足す`() {
        assertEquals(41, Capacity.of(41, 0, 135))
        assertEquals(50, Capacity.of(41, 9, 135))
    }

    @Test
    fun `標準より少なくも上限より多くもならない`() {
        assertEquals(Capacity.VANILLA, Capacity.of(41, -30, 135))
        assertEquals(135, Capacity.of(41, 500, 135))
    }

    @Test
    fun `拡張すると増えた分がわかる`() {
        val change = Capacity.expand(41, 9, 135)
        assertEquals(Capacity.Change(41, 50), change)
        assertEquals(9, change.added)
    }

    @Test
    fun `上限を超える分は増やさない`() {
        assertEquals(Capacity.Change(130, 135), Capacity.expand(130, 9, 135))
        assertEquals(0, Capacity.expand(135, 9, 135).added)
    }

    @Test
    fun `上限を下げたあとでも拡張で減らしはしない`() {
        assertEquals(Capacity.Change(140, 140), Capacity.expand(140, 9, 135))
    }

    @Test
    fun `既定の容量が変わっても追加した分は残る`() {
        val extra = Capacity.extraFor(50, 41)
        assertEquals(9, extra)
        assertEquals(54, Capacity.of(45, extra, 135))
    }
}
