package jp.vcmc.enderchest.logic

/**
 * 開いている間のエンダーチェストの中身。
 * 最初の 27 枠は標準のエンダーチェスト、それより後ろは追加の枠。
 * 容量より後ろの枠（容量を減らしたときに残ったもの）も消さずに持ち、そのまま保存し直す。
 */
class SlotBuffer<T : Any>(vanilla: List<T?>, extra: List<T?>, val capacity: Int) {
    val vanillaSize: Int = vanilla.size

    private val slots: MutableList<T?> = ArrayList<T?>(maxOf(capacity, vanilla.size + extra.size)).apply {
        addAll(vanilla)
        addAll(extra)
        while (size < capacity) add(null)
    }

    init {
        require(capacity >= vanillaSize) { "容量 $capacity は標準のエンダーチェスト（$vanillaSize 枠）より小さくできない" }
    }

    /** 持っている枠の数。容量より多いことがある。 */
    val size: Int get() = slots.size

    operator fun get(index: Int): T? = slots[index]

    operator fun set(index: Int, value: T?) {
        slots[index] = value
    }

    /** 標準のエンダーチェストに入れる分。 */
    fun vanilla(): List<T?> = slots.subList(0, vanillaSize).toList()

    /** 追加の枠に入れる分。後ろの空きは含めない。 */
    fun extra(): List<T?> = slots.drop(vanillaSize).dropLastWhile { it == null }

    /** 容量より後ろにあって、画面に出ない物の数。 */
    fun hiddenCount(): Int = slots.drop(capacity).count { it != null }
}
