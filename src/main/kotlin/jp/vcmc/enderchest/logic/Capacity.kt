package jp.vcmc.enderchest.logic

/** エンダーチェストの容量（枠数）の計算。 */
object Capacity {
    /** 標準のエンダーチェストの枠数。中身はそのまま標準のエンダーチェストに入れておく。 */
    const val VANILLA = 27

    /** 既定の容量と、プレイヤーごとの追加分から、使える容量を求める。標準より少なくも、上限より多くもしない。 */
    fun of(defaultSlots: Int, extra: Int, max: Int): Int = (defaultSlots + extra).coerceIn(VANILLA, max.coerceAtLeast(VANILLA))

    /** 拡張の前後の容量。 */
    data class Change(val before: Int, val after: Int) {
        val added: Int get() = after - before
    }

    /** current から amount 枠増やす。上限を超える分は増やさない。上限を下げたあとでも減らしはしない。 */
    fun expand(current: Int, amount: Int, max: Int): Change {
        require(amount > 0) { "amount は 1 以上: $amount" }
        val after = (current + amount).coerceAtMost(max).coerceAtLeast(current)
        return Change(current, after)
    }

    /** 容量を capacity 枠にするための追加分。既定の容量が変わっても、追加した分は保たれる。 */
    fun extraFor(capacity: Int, defaultSlots: Int): Int = capacity - defaultSlots
}
