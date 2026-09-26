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
 * Santali deliberately falls back to HINDI, not English and never machine
 * translation: published EN->Santali MT scores 4.7-7.3 BLEU, which is unsafe
 * for a safety instruction, and a Santali-speaking worker in Jharkhand is far
 * more likely to follow Hindi than English. Fill [sat] only with lines written
 * and recorded by a native speaker.
 */
data class T(val en: String, val hi: String, val sat: String? = null) {
    fun of(lang: Lang): String = when (lang) {
        Lang.EN -> en
        Lang.HI -> hi
        Lang.SAT -> sat ?: hi
    }
}

val LocalLang = staticCompositionLocalOf { Lang.EN }

@Composable
fun t(text: T): String = text.of(LocalLang.current)
