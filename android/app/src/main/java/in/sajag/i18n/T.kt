package `in`.sajag.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

enum class Lang(val label: String) {
    EN("English"),
    HI("हिन्दी"),
    SAT("Santali · ᱥᱟᱱᱛᱟᱲᱤ"),
}

/**
 * One piece of user-facing text in every supported language.
 *
 * Santali (Ol Chiki) comes from [sat] if given, else from the [SantaliText]
 * table keyed by the English line. Anything still missing falls back to HINDI,
 * not English: a Santali-speaking worker in Jharkhand is far more likely to
 * follow Hindi than English.
 *
 * TODO(native speaker): the table is a first draft and must be reviewed line
 * by line by a native Santali speaker before a pilot.
 */
data class T(val en: String, val hi: String, val sat: String? = null) {
    fun of(lang: Lang): String = when (lang) {
        Lang.EN -> en
        Lang.HI -> hi
        Lang.SAT -> santali ?: hi
    }

    /** The Ol Chiki line, or null if this text has no Santali yet. */
    val santali: String? get() = sat ?: SantaliText.lines[en]
}

val LocalLang = staticCompositionLocalOf { Lang.EN }

@Composable
fun t(text: T): String = text.of(LocalLang.current)
