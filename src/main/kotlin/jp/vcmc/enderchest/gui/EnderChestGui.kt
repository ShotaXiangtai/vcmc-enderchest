package jp.vcmc.enderchest.gui

import jp.vcmc.enderchest.VcmcEnderChestPlugin
import jp.vcmc.enderchest.i18n.Language
import jp.vcmc.enderchest.logic.Layout
import jp.vcmc.enderchest.logic.SlotBuffer
import jp.vcmc.enderchest.logic.SlotRole
import jp.vcmc.enderchest.storage.EnderChestData
import jp.vcmc.enderchest.storage.Recovery
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.block.EnderChest
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * 拡張したエンダーチェストの画面。
 * 開いた時点の中身を SlotBuffer に読み、画面での変更は次の tick に保存する。
 * 閉じたとき、ページを切り替えるとき、ログアウトとサーバーの停止のときにも保存する。
 * 画面の処理はすべてプレイヤーのリージョンで行う。
 */
class EnderChestGui(
    private val plugin: VcmcEnderChestPlugin,
    private val data: EnderChestData,
    private val recovery: Recovery,
) : Listener {

    private class Session(val owner: UUID, val buffer: SlotBuffer<ItemStack>, val layout: Layout, val lid: Location?) {
        /** 今開いているページの画面。 */
        var view: View? = null

        /** ページを切り替えている間は true。このときの InventoryCloseEvent ではセッションを終えない。 */
        var switching = false

        /** ページの切り替えを次の tick に予約済み。連打で何度も切り替えないため。 */
        var switchPending = false

        /** 保存を次の tick に予約済み。 */
        var savePending = false
    }

    private class View(val session: Session, val page: Int) : InventoryHolder {
        lateinit var inv: Inventory

        override fun getInventory(): Inventory = inv
    }

    private val sessions = ConcurrentHashMap<UUID, Session>()

    /** 蓋を開けているエンダーチェストと、開いている人数。 */
    private val lidViewers = ConcurrentHashMap<Location, Int>()

    private val controls = Controls(plugin, plugin.messages)
    private val messages get() = plugin.messages

    /**
     * エンダーチェストを開く。プレイヤーのリージョンから呼ぶこと。
     * lid は右クリックしたエンダーチェストのブロック。コマンドから開くときは null。
     */
    fun open(player: Player, lid: Block?): Boolean {
        sessions[player.uniqueId]?.let { old ->
            val current = old.view
            if (current != null && player.openInventory.topInventory.getHolder(false) === current) return true
            // 閉じたことを知らないまま残っていたセッション。中身を保存してから開き直す。
            close(player, old)
        }

        val capacity = plugin.capacityOf(player.uniqueId)
        val buffer = try {
            data.load(player, capacity)
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "${player.name} のエンダーチェストを読み込めませんでした", e)
            messages.send(player, "open-failed")
            return false
        }

        val session = Session(player.uniqueId, buffer, Layout(buffer.capacity), lid?.location)
        sessions[player.uniqueId] = session
        if (!show(player, session, 0)) {
            sessions.remove(player.uniqueId, session)
            messages.send(player, "open-failed")
            return false
        }
        if (lid != null) openLid(lid) else player.playSound(OPEN_SOUND)
        buffer.hiddenCount().takeIf { it > 0 }?.let { messages.send(player, "hidden-items", "count" to it) }
        return true
    }

    /** サーバーの停止時に呼ぶ。このときリスナーはもう動かないので、開いている画面の中身をここで保存する。 */
    fun saveAll() {
        for (session in sessions.values.toList()) {
            val player = Bukkit.getPlayer(session.owner) ?: continue
            val view = session.view ?: continue
            sync(view)
            persist(player, session)
            // 停止までの間に画面から取り出して、保存した分と二重にならないようにする。
            view.inv.clear()
        }
        sessions.clear()
    }

    private fun show(player: Player, session: Session, page: Int): Boolean {
        val layout = session.layout
        val language = Language.of(player)
        val view = View(session, page)
        val title = if (layout.paged) {
            messages.text(language, "title-page", "page" to page + 1, "pages" to layout.pageCount)
        } else {
            messages.text(language, "title")
        }
        val inv = Bukkit.createInventory(view, layout.size(), title)
        view.inv = inv
        for (slot in 0 until inv.size) {
            val item = when (val role = layout.role(page, slot)) {
                is SlotRole.Storage -> session.buffer[role.index]
                SlotRole.Locked -> controls.locked(language)
                SlotRole.Filler -> controls.filler()
                SlotRole.Previous -> controls.previous(language)
                SlotRole.Next -> controls.next(language)
                SlotRole.Info -> controls.info(language, layout.capacity, page + 1, layout.pageCount)
            }
            inv.setItem(slot, item)
        }
        session.view = view
        return player.openInventory(inv) != null
    }

    /** 画面の中身を SlotBuffer へ写す。画面に置くだけのアイテムが紛れ込んでいたら捨てる。 */
    private fun sync(view: View) {
        val layout = view.session.layout
        for (slot in 0 until view.inv.size) {
            val role = layout.role(view.page, slot) as? SlotRole.Storage ?: continue
            val item = view.inv.getItem(slot)?.takeUnless { it.isEmpty || controls.isControl(it) }
            view.session.buffer[role.index] = item?.clone()
        }
    }

    private fun persist(player: Player, session: Session) {
        try {
            data.save(player, session.buffer)
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "${player.name} のエンダーチェストを保存できませんでした", e)
            recovery.write(session.owner, session.buffer)
        }
    }

    /** 中身を保存してセッションを終える。画面に残ったアイテムは消して、保存した分と二重にならないようにする。 */
    private fun close(player: Player, session: Session) {
        session.view?.let { view ->
            sync(view)
            persist(player, session)
            view.inv.clear()
        }
        finish(player, session)
    }

    private fun finish(player: Player, session: Session) {
        if (!sessions.remove(session.owner, session)) return
        val lid = session.lid
        if (lid != null) closeLid(lid) else player.playSound(CLOSE_SOUND)
        removeControls(player)
    }

    /** 画面に置くだけのアイテムがプレイヤーの手に渡っていたら消す。通常は起きない。 */
    private fun removeControls(player: Player) {
        val inventory = player.inventory
        for (slot in 0 until inventory.size) {
            if (controls.isControl(inventory.getItem(slot))) inventory.setItem(slot, null)
        }
        if (controls.isControl(player.itemOnCursor)) player.setItemOnCursor(null)
    }

    private fun requestSave(player: Player, view: View) {
        val session = view.session
        if (session.savePending) return
        session.savePending = true
        plugin.runNextTick(player) {
            session.savePending = false
            // 閉じたときや切り替えたときは、その場で保存している。
            if (sessions[session.owner] !== session || session.view !== view) return@runNextTick
            sync(view)
            persist(player, session)
        }
    }

    private fun switchPage(player: Player, view: View, target: Int) {
        val session = view.session
        if (session.switchPending) return
        session.switchPending = true
        // クリックの処理中に画面を開き直してはいけないので、次の tick で切り替える。
        plugin.runNextTick(player) {
            session.switchPending = false
            if (sessions[session.owner] !== session || session.view !== view) return@runNextTick
            if (player.openInventory.topInventory.getHolder(false) !== view) return@runNextTick
            sync(view)
            persist(player, session)
            session.switching = true
            val opened = try {
                show(player, session, session.layout.clampPage(target))
            } finally {
                session.switching = false
            }
            // 開けなかったときは、前のページはもう閉じて保存済み。
            if (!opened) finish(player, session)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onClick(event: InventoryClickEvent) {
        val top = event.view.topInventory
        val view = top.getHolder(false) as? View ?: return
        val player = event.whoClicked as? Player ?: return
        if (event.clickedInventory == top) {
            // Q キーや数字キーは、カーソルが乗っていただけの誤操作なのでページを切り替えない。
            val pressed = event.click.isLeftClick || event.click.isRightClick
            when (view.session.layout.role(view.page, event.slot)) {
                is SlotRole.Storage -> Unit
                SlotRole.Previous -> {
                    event.isCancelled = true
                    if (pressed) switchPage(player, view, view.page - 1)
                    return
                }
                SlotRole.Next -> {
                    event.isCancelled = true
                    if (pressed) switchPage(player, view, view.page + 1)
                    return
                }
                else -> {
                    event.isCancelled = true
                    return
                }
            }
        }
        // 下の持ち物側の操作でも、Shift クリックなどで上の中身が変わる。
        if (!event.isCancelled) requestSave(player, view)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDrag(event: InventoryDragEvent) {
        val top = event.view.topInventory
        val view = top.getHolder(false) as? View ?: return
        val layout = view.session.layout
        if (event.rawSlots.any { it < top.size && layout.role(view.page, it) !is SlotRole.Storage }) {
            event.isCancelled = true
            return
        }
        val player = event.whoClicked as? Player ?: return
        if (!event.isCancelled) requestSave(player, view)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onClose(event: InventoryCloseEvent) {
        val view = event.inventory.getHolder(false) as? View ?: return
        val player = event.player as? Player ?: return
        val session = view.session
        if (sessions[session.owner] !== session || session.view !== view) return
        sync(view)
        persist(player, session)
        if (!session.switching) finish(player, session)
    }

    /** 通常は先に InventoryCloseEvent で保存済み。念のため、残っていれば保存する。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        val session = sessions[event.player.uniqueId] ?: return
        close(event.player, session)
    }

    private fun openLid(block: Block) {
        val location = block.location
        if (lidViewers.merge(location, 1, Int::plus) != 1) return
        try {
            (block.getState(false) as? EnderChest)?.open()
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "エンダーチェストの蓋を開けられませんでした", e)
        }
    }

    /** 蓋はそのブロックのリージョンで閉じる。プレイヤーが離れた場所にいることもある。 */
    private fun closeLid(location: Location) {
        if (!plugin.isEnabled) return
        Bukkit.getRegionScheduler().run(plugin, location) { _ ->
            val left = lidViewers.computeIfPresent(location) { _, count -> if (count <= 1) null else count - 1 }
            if (left != null) return@run
            val world = location.world ?: return@run
            if (!world.isChunkLoaded(location.blockX shr 4, location.blockZ shr 4)) return@run
            try {
                (location.block.getState(false) as? EnderChest)?.close()
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "エンダーチェストの蓋を閉じられませんでした", e)
            }
        }
    }

    private companion object {
        val OPEN_SOUND: Sound = Sound.sound(Key.key("block.ender_chest.open"), Sound.Source.BLOCK, 0.5f, 1.0f)
        val CLOSE_SOUND: Sound = Sound.sound(Key.key("block.ender_chest.close"), Sound.Source.BLOCK, 0.5f, 1.0f)
    }
}
