package jp.vcmc.enderchest

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

/**
 * プレイヤーごとの追加の枠数を capacity.yml に保存する。オフラインのプレイヤーの分も変えられる。
 * 既定の容量との差を持つので、config.yml の default-slots を変えても、拡張した分は保たれる。
 * どのスレッドから呼んでもよい。保存は saveLater に渡した処理（別スレッド）で行う。
 */
class CapacityStore(
    private val file: File,
    private val logger: Logger,
    private val saveLater: (Runnable) -> Unit,
) {
    private val extras = ConcurrentHashMap<UUID, Int>()
    private val saveLock = Any()

    /** 読み込みに失敗したときは false。ファイルを上書きして中身を失わないよう、変更も保存もしない。 */
    @Volatile
    var writable: Boolean = true
        private set

    fun load() {
        extras.clear()
        writable = true
        if (!file.exists()) return
        try {
            val yaml = YamlConfiguration().apply { load(file) }
            val players = yaml.getConfigurationSection(PLAYERS) ?: return
            for (key in players.getKeys(false)) {
                val id = runCatching { UUID.fromString(key) }.getOrNull()
                if (id == null || !players.isInt(key)) {
                    logger.warning("${file.name} の $PLAYERS.$key は読めないので無視します")
                    continue
                }
                players.getInt(key).takeIf { it != 0 }?.let { extras[id] = it }
            }
        } catch (e: Exception) {
            writable = false
            logger.log(Level.SEVERE, "${file.name} を読み込めませんでした。容量はすべて既定にし、変更も保存もしません", e)
        }
    }

    fun extra(id: UUID): Int = extras[id] ?: 0

    /**
     * 追加の枠数を change で変える。change は今の値を受け取り、新しい値と結果を返す。
     * 同じプレイヤーへの変更が同時に来ても、順番に適用する。
     */
    fun <R> update(id: UUID, change: (Int) -> Pair<Int, R>): R {
        check(writable) { "${file.name} を読み込めていないので変更できません" }
        val result = arrayOfNulls<Any?>(1)
        extras.compute(id) { _, old ->
            val (next, value) = change(old ?: 0)
            result[0] = value
            next.takeIf { it != 0 }
        }
        saveLater(Runnable { saveSafely() })
        @Suppress("UNCHECKED_CAST")
        return result[0] as R
    }

    /** 今の内容をすぐに保存する。停止時に呼ぶ。 */
    fun save() {
        if (!writable) return
        synchronized(saveLock) {
            val yaml = YamlConfiguration()
            yaml.options().setHeader(HEADER)
            extras.entries.sortedBy { it.key.toString() }.forEach { (id, extra) -> yaml.set("$PLAYERS.$id", extra) }
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(yaml.saveToString(), Charsets.UTF_8)
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun saveSafely() {
        try {
            save()
        } catch (e: IOException) {
            logger.log(Level.SEVERE, "${file.name} を保存できませんでした", e)
        }
    }

    private companion object {
        const val PLAYERS = "players"
        val HEADER = listOf(
            "プレイヤーごとの追加の枠数（config.yml の default-slots との差）。",
            "/enderchest expand と /enderchest set で変わります。サーバーの起動中は手で編集しないでください。",
        )
    }
}
