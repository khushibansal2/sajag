package `in`.sajag.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import `in`.sajag.i18n.Lang
import java.util.Locale

/**
 * The voice guide. No safety instruction in a drill is text-only: a worker
 * with a cap lamp on is listening, not reading, and many recruits read
 * neither English nor Hindi comfortably.
 *
 * Uses the phone's offline TTS voices. Santali speaks Hindi until recorded
 * native-speaker audio replaces this (see i18n/T.kt for why not MT).
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
        val locale = if (lang == Lang.EN) Locale("en", "IN") else Locale("hi", "IN")
        if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sajag-guide")
    }

    fun stop() {
        if (ready) tts.stop()
    }

    fun shutdown() = tts.shutdown()
}

@Composable
fun rememberGuide(): Guide {
    val context = LocalContext.current
    val guide = remember { Guide(context) }
    DisposableEffect(guide) { onDispose { guide.shutdown() } }
    return guide
}
