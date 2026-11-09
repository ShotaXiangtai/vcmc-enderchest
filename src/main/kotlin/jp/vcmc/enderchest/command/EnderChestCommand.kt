package jp.vcmc.enderchest.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import jp.vcmc.enderchest.VcmcEnderChestPlugin
import jp.vcmc.enderchest.logic.Capacity
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * /enderchest（別名 /ec）
 * - /enderchest: 自分のエンダーチェストを開く
 * - /enderchest info [プレイヤー]: 容量を見る
 * - /enderchest expand <プレイヤー> <枠数>: 容量を増やす（投票ショップなどからコンソールで実行する）
 * - /enderchest set <プレイヤー> <枠数>: 容量を決める
 * - /enderchest reload: 設定を再読み込みする
 */
class EnderChestCommand(private val plugin: VcmcEnderChestPlugin) {
    private val messages get() = plugin.messages

    fun register() {
        plugin.lifecycleManager.registerEventHandler(
            LifecycleEvents.COMMANDS.newHandler { event ->
                event.registrar().register(build(), "エンダーチェストを開きます", listOf("ec"))
            },
        )
    }

    private fun build(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("enderchest")
            .requires { source -> ALL_PERMISSIONS.any(source.sender::hasPermission) }
            .executes(::openSelf)
            .then(
                Commands.literal("info")
                    .executes(::infoSelf)
                    .then(playerArgument().requires { it.sender.hasPermission(PERM_INFO) }.executes(::infoOther)),
            )
            .then(
                Commands.literal("expand")
                    .requires { it.sender.hasPermission(PERM_EXPAND) }
                    .then(playerArgument().then(Commands.argument(SLOTS, IntegerArgumentType.integer(1)).executes(::expand))),
            )
            .then(
                Commands.literal("set")
                    .requires { it.sender.hasPermission(PERM_SET) }
                    .then(playerArgument().then(Commands.argument(SLOTS, IntegerArgumentType.integer(1)).executes(::set))),
            )
            .then(
                Commands.literal("reload")
                    .requires { it.sender.hasPermission(PERM_RELOAD) }
                    .executes(::reload),
            )
            .build()

    private fun playerArgument(): RequiredArgumentBuilder<CommandSourceStack, String> =
        Commands.argument(PLAYER, StringArgumentType.word()).suggests { _, builder ->
            val prefix = builder.remainingLowerCase
            Bukkit.getOnlinePlayers().map { it.name }.filter { it.lowercase().startsWith(prefix) }.sorted().forEach(builder::suggest)
            builder.buildFuture()
        }

    private fun selfPlayer(context: CommandContext<CommandSourceStack>): Player? {
        val source = context.source
        val player = source.executor as? Player ?: source.sender as? Player
        if (player == null) messages.send(source.sender, "player-only")
        return player
    }

    private fun openSelf(context: CommandContext<CommandSourceStack>): Int {
        val player = selfPlayer(context) ?: return 0
        if (!player.hasPermission(PERM_OPEN)) return 0
        player.scheduler.run(plugin, { _ -> plugin.gui.open(player, null) }, null)
        return Command.SINGLE_SUCCESS
    }

    private fun infoSelf(context: CommandContext<CommandSourceStack>): Int {
        val player = selfPlayer(context) ?: return 0
        messages.send(player, "info-self", "capacity" to plugin.capacityOf(player.uniqueId), "max" to plugin.settings.maxSlots)
        return Command.SINGLE_SUCCESS
    }

    private fun infoOther(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender
        val target = resolve(sender, StringArgumentType.getString(context, PLAYER)) ?: return 0
        val settings = plugin.settings
        messages.send(
            sender,
            "info-other",
            "player" to nameOf(target, context),
            "capacity" to plugin.capacityOf(target.uniqueId),
            "default" to settings.defaultSlots,
            "extra" to plugin.capacities.extra(target.uniqueId),
            "max" to settings.maxSlots,
        )
        return Command.SINGLE_SUCCESS
    }

    private fun expand(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender
        val target = resolve(sender, StringArgumentType.getString(context, PLAYER)) ?: return 0
        if (!checkWritable(sender)) return 0
        val amount = IntegerArgumentType.getInteger(context, SLOTS)
        val settings = plugin.settings
        val change = plugin.capacities.update(target.uniqueId) { extra ->
            val current = Capacity.of(settings.defaultSlots, extra, settings.maxSlots)
            val change = Capacity.expand(current, amount, settings.maxSlots)
            val next = if (change.added > 0) Capacity.extraFor(change.after, settings.defaultSlots) else extra
            next to change
        }
        val name = nameOf(target, context)
        val online = Bukkit.getPlayer(target.uniqueId)
        if (change.added == 0) {
            messages.send(sender, "at-max", "player" to name, "max" to settings.maxSlots)
            online?.let { messages.send(it, "at-max-notice", "max" to settings.maxSlots) }
            if (sender !is Player) plugin.logger.warning("$name のエンダーチェストは上限のため拡張しませんでした（${amount} 枠の拡張）")
            return 0
        }
        val key = if (change.added < amount) "expanded-partly" else "expanded"
        messages.send(sender, key, "player" to name, "added" to change.added, "before" to change.before, "after" to change.after)
        online?.let { messages.send(it, "expanded-notice", "added" to change.added, "after" to change.after) }
        return Command.SINGLE_SUCCESS
    }

    private fun set(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender
        val target = resolve(sender, StringArgumentType.getString(context, PLAYER)) ?: return 0
        if (!checkWritable(sender)) return 0
        val slots = IntegerArgumentType.getInteger(context, SLOTS)
        val settings = plugin.settings
        if (slots !in Capacity.VANILLA..settings.maxSlots) {
            messages.send(sender, "set-out-of-range", "min" to Capacity.VANILLA, "max" to settings.maxSlots)
            return 0
        }
        val before = plugin.capacities.update(target.uniqueId) { extra ->
            Capacity.extraFor(slots, settings.defaultSlots) to Capacity.of(settings.defaultSlots, extra, settings.maxSlots)
        }
        messages.send(sender, "set-done", "player" to nameOf(target, context), "before" to before, "after" to slots)
        return Command.SINGLE_SUCCESS
    }

    private fun reload(context: CommandContext<CommandSourceStack>): Int {
        plugin.reloadSettings()
        messages.send(context.source.sender, "reloaded")
        return Command.SINGLE_SUCCESS
    }

    private fun checkWritable(sender: CommandSender): Boolean {
        if (plugin.capacities.writable) return true
        messages.send(sender, "store-unavailable")
        return false
    }

    /** オンラインなら名前で、オフラインならサーバーに来たことのある名前から探す。Mojang への問い合わせはしない。 */
    private fun resolve(sender: CommandSender, name: String): OfflinePlayer? {
        val player = Bukkit.getPlayerExact(name) ?: Bukkit.getOfflinePlayerIfCached(name)
        if (player == null) messages.send(sender, "player-not-found", "player" to name)
        return player
    }

    private fun nameOf(player: OfflinePlayer, context: CommandContext<CommandSourceStack>): String =
        player.name ?: StringArgumentType.getString(context, PLAYER)

    private companion object {
        const val PLAYER = "player"
        const val SLOTS = "slots"
        const val PERM_OPEN = "vcmcenderchest.command.open"
        const val PERM_INFO = "vcmcenderchest.admin.info"
        const val PERM_EXPAND = "vcmcenderchest.admin.expand"
        const val PERM_SET = "vcmcenderchest.admin.set"
        const val PERM_RELOAD = "vcmcenderchest.admin.reload"
        val ALL_PERMISSIONS = listOf(PERM_OPEN, PERM_INFO, PERM_EXPAND, PERM_SET, PERM_RELOAD)
    }
}
