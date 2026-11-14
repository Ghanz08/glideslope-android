package id.glideslope.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.work.WorkManager
import id.glideslope.app.data.SessionStore
import id.glideslope.app.sync.PullWorker
import id.glideslope.app.ui.GlideslopeTheme
import id.glideslope.app.ui.Root

// FragmentActivity because BiometricPrompt needs one
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen() // before super: swaps the starting theme for the app theme once it lets go
        super.onCreate(savedInstanceState)
        val signedIn = SessionStore(this).get()?.aal == "aal2"
        if (savedInstanceState == null) Boot.begin(needsData = signedIn)
        splash.setKeepOnScreenCondition { Boot.keepSplash() }
        splash.setOnExitAnimationListener { p ->
            // the lock screen is the same blue: the mark grows and fades first, then the blue lets go (no hard cut)
            p.iconView.animate().scaleX(1.15f).scaleY(1.15f).alpha(0f).setDuration(300).setInterpolator(android.view.animation.AccelerateInterpolator()).start()
            p.view.animate().alpha(0f).setStartDelay(160).setDuration(420).setInterpolator(android.view.animation.DecelerateInterpolator()).withEndAction { p.remove() }.start()
        }
        if (signedIn) {
            // the first pull starts behind the splash; it lets go when the work has finished
            PullWorker.now(this)
            WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("tarik").observe(this) { infos ->
                if (infos.any { it.state.isFinished }) Boot.dataReady = true
            }
        }
        // screenshots are allowed (owner's choice); privacy mode still hides amounts inside the app
        enableEdgeToEdge()
        setContent { GlideslopeTheme { Root(this) } }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) AppLock.onLeave() // a rotation is not leaving
    }
}
