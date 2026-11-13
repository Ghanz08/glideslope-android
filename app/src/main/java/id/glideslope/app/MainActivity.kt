package id.glideslope.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import id.glideslope.app.ui.GlideslopeTheme
import id.glideslope.app.ui.Root

// FragmentActivity because BiometricPrompt needs one
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
