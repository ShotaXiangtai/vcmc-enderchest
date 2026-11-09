package jp.vcmc.enderchest

import jp.vcmc.enderchest.logic.Capacity
import org.bukkit.configuration.ConfigurationSection

/** config.yml の容量の設定。 */
data class Settings(val defaultSlots: Int, val maxSlots: Int) {
    companion object {
        const val DEFAULT_SLOTS = 41
        const val MAX_SLOTS = 135

        /** 範囲外の値は直して使い、warn で知らせる。 */
        fun from(config: ConfigurationSection, warn: (String) -> Unit): Settings {
            var max = config.getInt("max-slots", MAX_SLOTS)
            if (max < Capacity.VANILLA) {
                warn("max-slots は ${Capacity.VANILLA} 以上にしてください。${Capacity.VANILLA} を使います。")
                max = Capacity.VANILLA
            }
            var defaultSlots = config.getInt("default-slots", DEFAULT_SLOTS)
            if (defaultSlots !in Capacity.VANILLA..max) {
                val fixed = defaultSlots.coerceIn(Capacity.VANILLA, max)
                warn("default-slots は ${Capacity.VANILLA}〜$max にしてください。$fixed を使います。")
                defaultSlots = fixed
            }
            return Settings(defaultSlots, max)
        }
    }
}
