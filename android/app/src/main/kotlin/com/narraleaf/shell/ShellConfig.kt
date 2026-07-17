package com.narraleaf.shell

import android.content.pm.ActivityInfo
import android.content.res.AssetManager
import org.json.JSONObject

/**
 * The per-game settings the repacker injects as `assets/shell-config.json`.
 *
 * Parsed with the platform's own JSON reader — the shell carries no third-party
 * libraries. Every field falls back to a default, so an older template paired
 * with a newer writer (or the reverse) degrades instead of crashing.
 */
data class ShellConfig(
    val schemaVersion: Int,
    val orientation: Orientation,
    val backgroundColor: Int,
    /**
     * Opaque token the payload decoder needs, injected only when the packer
     * encoded this build's payload. Null means the payload is plain and is
     * served as-is — one shell runs both kinds of game.
     */
    val contentKey: String?,
) {

    enum class Orientation {
        LANDSCAPE,
        PORTRAIT,
        AUTO;

        /** The ActivityInfo constant this maps onto at runtime. */
        fun toActivityInfo(): Int = when (this) {
            LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        companion object {
            fun parse(value: String?): Orientation = when (value?.lowercase()) {
                "portrait" -> PORTRAIT
                "auto" -> AUTO
                else -> LANDSCAPE
            }
        }
    }

    companion object {
        const val PATH = "shell-config.json"

        /** Matches the pre-boot background the web export paints. */
        private const val DEFAULT_BACKGROUND = 0xFF000000.toInt()

        val DEFAULT = ShellConfig(
            schemaVersion = 1,
            orientation = Orientation.LANDSCAPE,
            backgroundColor = DEFAULT_BACKGROUND,
            contentKey = null,
        )

        /**
         * Read the injected config, falling back to [DEFAULT] when it is
         * absent or unreadable — a template running without a repack (the CI
         * smoke test) still has to boot.
         */
        fun load(assets: AssetManager): ShellConfig {
            val text = runCatching {
                assets.open(PATH).bufferedReader().use { it.readText() }
            }.getOrNull() ?: return DEFAULT

            val json = runCatching { JSONObject(text) }.getOrNull() ?: return DEFAULT
            return ShellConfig(
                schemaVersion = json.optInt("schemaVersion", DEFAULT.schemaVersion),
                orientation = Orientation.parse(json.optString("orientation", null)),
                backgroundColor = parseColor(json.optString("backgroundColor", null))
                    ?: DEFAULT.backgroundColor,
                contentKey = json.optString("contentKey", null)?.takeIf { it.isNotEmpty() },
            )
        }

        /** #rgb / #rrggbb / #aarrggbb, or null when unparseable. */
        private fun parseColor(value: String?): Int? {
            val text = value?.trim()?.removePrefix("#") ?: return null
            val hex = when (text.length) {
                3 -> text.map { "$it$it" }.joinToString("")
                6, 8 -> text
                else -> return null
            }
            val parsed = hex.toLongOrNull(16) ?: return null
            return if (hex.length == 6) (0xFF000000L or parsed).toInt() else parsed.toInt()
        }
    }
}
