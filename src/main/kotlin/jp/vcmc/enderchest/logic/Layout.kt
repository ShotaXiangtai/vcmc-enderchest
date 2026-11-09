package jp.vcmc.enderchest.logic

/** 画面の各スロットの役割。 */
sealed interface SlotRole {
    /** 中身を入れる枠。index は 0 から数えた枠の番号。 */
    data class Storage(val index: Int) : SlotRole

    /** 容量が足りずに使えない枠。 */
    data object Locked : SlotRole

    /** ページ切り替えの行の空き。 */
    data object Filler : SlotRole

    data object Previous : SlotRole

    data object Next : SlotRole

    /** 容量とページ数の表示。 */
    data object Info : SlotRole
}

/**
 * 容量に合わせた画面の組み立て。
 * 54 枠までは 1 画面に収め、行の残りは使えない枠として表示する。
 * それより多いときは 45 枠ずつのページに分け、最後の行にページの切り替えを置く。
 */
class Layout(val capacity: Int) {
    init {
        require(capacity > 0) { "capacity は 1 以上: $capacity" }
    }

    val paged: Boolean = capacity > SINGLE_PAGE_MAX
    val pageCount: Int = if (paged) (capacity + PAGE_SLOTS - 1) / PAGE_SLOTS else 1

    fun rows(): Int = if (paged) MAX_ROWS else (capacity + ROW - 1) / ROW

    fun size(): Int = rows() * ROW

    fun clampPage(page: Int): Int = page.coerceIn(0, pageCount - 1)

    fun role(page: Int, slot: Int): SlotRole {
        require(page in 0 until pageCount) { "page が範囲外: $page" }
        require(slot in 0 until size()) { "slot が範囲外: $slot" }
        if (!paged) return if (slot < capacity) SlotRole.Storage(slot) else SlotRole.Locked
        if (slot < PAGE_SLOTS) {
            val index = page * PAGE_SLOTS + slot
            return if (index < capacity) SlotRole.Storage(index) else SlotRole.Locked
        }
        return when (slot) {
            PREVIOUS -> if (page > 0) SlotRole.Previous else SlotRole.Filler
            INFO -> SlotRole.Info
            NEXT -> if (page < pageCount - 1) SlotRole.Next else SlotRole.Filler
            else -> SlotRole.Filler
        }
    }

    companion object {
        const val ROW = 9
        const val MAX_ROWS = 6
        const val SINGLE_PAGE_MAX = MAX_ROWS * ROW
        const val PAGE_SLOTS = (MAX_ROWS - 1) * ROW
        const val PREVIOUS = PAGE_SLOTS
        const val INFO = PAGE_SLOTS + 4
        const val NEXT = PAGE_SLOTS + 8
    }
}
