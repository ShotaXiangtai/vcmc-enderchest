package jp.vcmc.enderchest.listener

import jp.vcmc.enderchest.gui.EnderChestGui
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Statistic
import org.bukkit.block.EnderChest
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

/** 設置したエンダーチェストを右クリックしたら、標準の画面の代わりに拡張した画面を開く。 */
class EnderChestBlockListener(private val gui: EnderChestGui) : Listener {

    /** 保護プラグインが先に使用を止められるよう、HIGH で受け取る。 */
    @EventHandler(priority = EventPriority.HIGH)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK || event.hand != EquipmentSlot.HAND) return
        val block = event.clickedBlock ?: return
        if (block.type != Material.ENDER_CHEST || event.useInteractedBlock() == Event.Result.DENY) return
        val player = event.player
        if (player.gameMode == GameMode.SPECTATOR) return
        // バニラと同じく、スニーク中に何かを持っていればブロックは使わず、手に持った物を使う。
        val inventory = player.inventory
        if (player.isSneaking && !(inventory.itemInMainHand.isEmpty && inventory.itemInOffHand.isEmpty)) return
        val state = block.getState(false) as? EnderChest ?: return
        // 上がふさがっているときは、バニラでも開かない。
        if (state.isBlocked) return

        event.isCancelled = true
        if (gui.open(player, block)) player.incrementStatistic(Statistic.ENDERCHEST_OPENED)
    }
}
