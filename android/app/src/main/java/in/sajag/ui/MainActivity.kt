package `in`.sajag.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import `in`.sajag.ui.theme.SajagTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge, as Android 15 requires of apps targeting it: the app
        // draws behind the status and navigation bars and pads its own content.
        // The app is always light, so the bar icons start dark; the drill
        // switches them to light over the camera (see SajagRoot).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(LIGHT_SCRIM, DARK_SCRIM),
        )
        super.onCreate(savedInstanceState)
        setContent { SajagTheme { SajagRoot() } }
    }

    private companion object {
        /** The same scrims androidx uses by default for three-button navigation. */
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
