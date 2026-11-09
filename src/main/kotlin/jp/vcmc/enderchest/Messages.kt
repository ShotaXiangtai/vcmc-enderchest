package jp.vcmc.enderchest

import jp.vcmc.enderchest.i18n.Language
import jp.vcmc.enderchest.i18n.Translations
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.plugin.java.JavaPlugin

/** 受け取る人の言語で文面を作る。日本語は config.yml の messages、ほかの言語は lang フォルダから読む。 */
class Messages(private val plugin: JavaPlugin) {
    private val translations = Translations(plugin)
    private val miniMessage = MiniMessage.miniMessage()

    fun reload() = translations.reload()

    private fun raw(language: Language, key: String): String =
        translations.string(language, key) ?: plugin.config.getString("messages.$key") ?: key

    /** args の値はタグとして解釈せず、そのまま表示する。 */
    fun text(language: Language, key: String, vararg args: Pair<String, Any>): Component =
        miniMessage.deserialize(raw(language, key), TagResolver.resolver(args.map { (name, value) -> Placeholder.unparsed(name, value.toString()) }))

    /** 文面が空のときは送らない。 */
    fun send(target: Audience, key: String, vararg args: Pair<String, Any>) {
        val language = Language.of(target)
        if (raw(language, key).isEmpty()) return
        target.sendMessage(text(language, key, *args))
    }

    /** アイテムの名前や説明。アイテムの名前は既定で斜体になるので、指定が無ければ斜体にしない。 */
    fun item(language: Language, key: String, vararg args: Pair<String, Any>): Component =
        text(language, key, *args).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
}
