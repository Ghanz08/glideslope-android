package id.glideslope.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import id.glideslope.app.ui.GlideslopeTheme
import id.glideslope.app.ui.Root

// FragmentActivity because BiometricPrompt needs one
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // before super: swaps the starting theme for the app theme once the first frame is ready
        super.onCreate(savedInstanceState)
        // screenshots are allowed (owner's choice); privacy mode still hides amounts inside the app
        enableEdgeToEdge()
        setContent { GlideslopeTheme { Root(this) } }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        AppLock.onBackground()
    }
}
