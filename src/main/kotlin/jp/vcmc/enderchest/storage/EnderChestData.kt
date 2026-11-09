package jp.vcmc.enderchest.storage

import jp.vcmc.enderchest.logic.Capacity
import jp.vcmc.enderchest.logic.SlotBuffer
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

/**
 * エンダーチェストの中身の読み書き。
 * 最初の 27 枠は標準のエンダーチェストにそのまま入れる。プラグインを外しても、その分は普通に使える。
 * 追加の枠は、プレイヤーのデータ（PersistentDataContainer）に 1 枠ずつ保存する。
 * どちらもプレイヤーのデータファイルに入るので、標準のエンダーチェストや持ち物と同時に保存される。
 * プレイヤーのリージョンか、サーバーの停止時に呼ぶこと。
 */
class EnderChestData(private val plugin: JavaPlugin) {
    private val extraKey = NamespacedKey(plugin, "extra")

    /** 読めない枠があるときは例外を投げる。空として開くと、閉じたときにその枠を消してしまうため。 */
    fun load(player: Player, capacity: Int): SlotBuffer<ItemStack> {
        val vanilla = player.enderChest.contents.map(::orNull)
        check(vanilla.size == Capacity.VANILLA) { "標準のエンダーチェストが ${vanilla.size} 枠です" }
        return SlotBuffer(vanilla, readExtra(player.persistentDataContainer), capacity.coerceAtLeast(vanilla.size))
    }

    fun save(player: Player, buffer: SlotBuffer<ItemStack>) {
        player.enderChest.contents = buffer.vanilla().toTypedArray()
        writeExtra(player.persistentDataContainer, buffer.extra())
    }

    private fun readExtra(data: PersistentDataContainer): List<ItemStack?> {
        val container = data.get(extraKey, PersistentDataType.TAG_CONTAINER) ?: return emptyList()
        val items = HashMap<Int, ItemStack>()
        for (key in container.keys) {
            val slot = key.key.removePrefix(SLOT_PREFIX).toIntOrNull()
            check(key.namespace == extraKey.namespace && slot != null && slot >= Capacity.VANILLA) { "知らないキーがあります: $key" }
            val bytes = checkNotNull(container.get(key, PersistentDataType.BYTE_ARRAY)) { "$key がバイト列ではありません" }
            items[slot - Capacity.VANILLA] = ItemStack.deserializeBytes(bytes)
        }
        val size = (items.keys.maxOrNull() ?: -1) + 1
        return List(size) { index -> items[index]?.let(::orNull) }
    }

    private fun writeExtra(data: PersistentDataContainer, extra: List<ItemStack?>) {
        if (extra.all { it == null }) {
            data.remove(extraKey)
            return
        }
        val container = data.adapterContext.newPersistentDataContainer()
        extra.forEachIndexed { index, item ->
            if (item != null) {
                val key = NamespacedKey(plugin, "$SLOT_PREFIX${Capacity.VANILLA + index}")
                container.set(key, PersistentDataType.BYTE_ARRAY, item.serializeAsBytes())
            }
        }
        data.set(extraKey, PersistentDataType.TAG_CONTAINER, container)
    }

    private fun orNull(item: ItemStack?): ItemStack? = item?.takeUnless { it.isEmpty }

    private companion object {
        const val SLOT_PREFIX = "slot_"
    }
}
