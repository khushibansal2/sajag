package `in`.sajag.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import `in`.sajag.i18n.Lang
import `in`.sajag.i18n.T
import java.util.Locale

/**
 * The voice guide. No safety instruction in a drill is text-only: a worker
 * with a cap lamp on is listening, not reading, and many recruits read
 * neither English nor Hindi comfortably.
 *
 * Uses the phone's offline TTS voices. Santali uses a Santali voice if the
 * phone has one; almost none do, so it otherwise speaks the HINDI line (a Hindi
 * voice cannot read Ol Chiki) while the screen shows Santali.
 */
class Guide(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: Pair<String, Lang>? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        pending?.let { (text, lang) -> say(text, lang) }
        pending = null
    }

    fun say(text: String, lang: Lang) {
        if (!ready) {
            pending = text to lang
            return
        }
        val locale = when (lang) {
            Lang.EN -> Locale("en", "IN")
            Lang.HI -> Locale("hi", "IN")
            Lang.SAT -> SANTALI
        }
        if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sajag-guide")
    }

    /** Speaks [text] in [lang], or in Hindi when Santali has no voice on this phone. */
    fun say(text: T, lang: Lang) = say(listOf(text), lang)

    fun say(lines: List<T>, lang: Lang) {
        val voice = if (lang == Lang.SAT && !hasSantaliVoice()) Lang.HI else lang
        say(lines.joinToString(" ") { it.of(voice) }, voice)
    }

    private fun hasSantaliVoice(): Boolean =
        ready && tts.isLanguageAvailable(SANTALI) >= TextToSpeech.LANG_AVAILABLE

    fun stop() {
        if (ready) tts.stop()
    }

    fun shutdown() = tts.shutdown()

    private companion object {
        val SANTALI: Locale = Locale("sat", "IN")
    }
}

@Composable
fun rememberGuide(): Guide {
    val context = LocalContext.current
    val guide = remember { Guide(context) }
    DisposableEffect(guide) { onDispose { guide.shutdown() } }
    return guide
}
