package jp.vcmc.enderchest

import jp.vcmc.enderchest.command.EnderChestCommand
import jp.vcmc.enderchest.gui.EnderChestGui
import jp.vcmc.enderchest.listener.EnderChestBlockListener
import jp.vcmc.enderchest.logic.Capacity
import jp.vcmc.enderchest.storage.EnderChestData
import jp.vcmc.enderchest.storage.Recovery
import org.bukkit.entity.Entity
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID
import java.util.logging.Level

class VcmcEnderChestPlugin : JavaPlugin() {
    lateinit var messages: Messages
        private set
    lateinit var capacities: CapacityStore
        private set
    lateinit var gui: EnderChestGui
        private set

    /** どのスレッドからも読む。読み直しのときは丸ごと入れ替える。 */
    @Volatile
    lateinit var settings: Settings
        private set

    override fun onEnable() {
        saveDefaultConfig()
        messages = Messages(this)
        reloadSettings()

        capacities = CapacityStore(File(dataFolder, "capacity.yml"), logger) { task ->
            server.asyncScheduler.runNow(this) { task.run() }
        }
        capacities.load()

        gui = EnderChestGui(this, EnderChestData(this), Recovery(this))
        server.pluginManager.registerEvents(gui, this)
        server.pluginManager.registerEvents(EnderChestBlockListener(gui), this)
        EnderChestCommand(this).register()
    }

    override fun onDisable() {
        // 停止時は InventoryCloseEvent のリスナーが呼ばれないので、開いている画面をここで保存する。
        if (::gui.isInitialized) gui.saveAll()
        if (::capacities.isInitialized) {
            try {
                capacities.save()
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "capacity.yml を保存できませんでした", e)
            }
        }
    }

    fun reloadSettings() {
        reloadConfig()
        settings = Settings.from(config) { logger.warning("[config.yml] $it") }
        messages.reload()
    }

    /** 使える容量（枠数）。 */
    fun capacityOf(player: UUID): Int {
        val settings = settings
        return Capacity.of(settings.defaultSlots, capacities.extra(player), settings.maxSlots)
    }

    /** entity を所有するリージョンの次の tick で実行する。ログアウト済みなら何もしない。 */
    fun runNextTick(entity: Entity, block: () -> Unit) {
        entity.scheduler.run(this, { _ -> block() }, null)
    }
}
