package jp.vcmc.enderchest.gui

import jp.vcmc.enderchest.Messages
import jp.vcmc.enderchest.i18n.Language
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

/**
 * 使えない枠やページ切り替えなど、画面に置くだけのアイテム。
 * 印を付けておき、万一プレイヤーの手に渡っても見分けて消せるようにする。
 */
class Controls(plugin: JavaPlugin, private val messages: Messages) {
    private val marker = NamespacedKey(plugin, "control")

    fun locked(language: Language): ItemStack =
        item(Material.GRAY_STAINED_GLASS_PANE, messages.item(language, "locked-name"), listOf(messages.item(language, "locked-lore")))

    fun filler(): ItemStack =
        ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            editMeta { meta ->
                meta.isHideTooltip = true
                mark(meta)
            }
        }

    fun previous(language: Language): ItemStack = item(Material.ARROW, messages.item(language, "previous-name"), emptyList())

    fun next(language: Language): ItemStack = item(Material.ARROW, messages.item(language, "next-name"), emptyList())

    fun info(language: Language, capacity: Int, page: Int, pages: Int): ItemStack =
        item(
            Material.BOOK,
            messages.item(language, "info-name", "capacity" to capacity),
            listOf(messages.item(language, "info-lore", "page" to page, "pages" to pages)),
        )

    fun isControl(item: ItemStack?): Boolean = item != null && !item.isEmpty && item.persistentDataContainer.has(marker)

    private fun item(material: Material, name: Component, lore: List<Component>): ItemStack =
        ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(name)
                meta.lore(lore)
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
                mark(meta)
            }
        }

    private fun mark(meta: ItemMeta) {
        meta.persistentDataContainer.set(marker, PersistentDataType.BYTE, 1)
    }
}
