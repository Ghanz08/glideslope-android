package id.glideslope.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import id.glideslope.app.ui.GlideslopeTheme
import id.glideslope.app.ui.Root

// FragmentActivity because BiometricPrompt needs one
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // no screenshots, and a blank card in Recents: amounts never show outside the app (F-14, 4.10)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
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
