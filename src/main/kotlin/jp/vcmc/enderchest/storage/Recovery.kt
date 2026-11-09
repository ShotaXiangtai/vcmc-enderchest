package jp.vcmc.enderchest.storage

import jp.vcmc.enderchest.logic.SlotBuffer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import java.util.logging.Level

/** 保存できなかった中身を、失わないようにファイルへ書き出す。 */
class Recovery(private val plugin: JavaPlugin) {
    fun write(owner: UUID, buffer: SlotBuffer<ItemStack>) {
        val file = File(File(plugin.dataFolder, "recovery"), "$owner-${TIME.format(LocalDateTime.now())}.yml")
        try {
            val yaml = YamlConfiguration()
            yaml.options().setHeader(listOf("保存できなかったエンダーチェストの中身。枠の番号ごとに ItemStack.serializeAsBytes の Base64 です。"))
            yaml.set("owner", owner.toString())
            for (index in 0 until buffer.size) {
                buffer[index]?.let { yaml.set("slots.$index", Base64.getEncoder().encodeToString(it.serializeAsBytes())) }
            }
            file.parentFile.mkdirs()
            yaml.save(file)
            plugin.logger.severe("エンダーチェストの中身を ${file.path} へ書き出しました。")
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "エンダーチェストの中身を書き出せませんでした (UUID: $owner)", e)
        }
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
