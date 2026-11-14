// Keeps the splash screen up while the app starts: at least long enough to see it, and, when signed in, until the first
// pull of data has finished (or [MAX] ms have passed, so a slow network never traps the owner on the splash).
package id.glideslope.app

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Boot {
    private const val MIN = 1400L
    private const val MAX = 3000L
    private var start = SystemClock.elapsedRealtime()
    @Volatile var dataReady = false

    /** True once the splash has been let go of: the lock prompt waits for it, so it never pops up over the splash. */
    var released by mutableStateOf(false)
        private set

    fun begin(needsData: Boolean) {
        start = SystemClock.elapsedRealtime()
        dataReady = !needsData
        released = false
    }

    fun keepSplash(): Boolean {
        val t = SystemClock.elapsedRealtime() - start
        val keep = t < MIN || (!dataReady && t < MAX)
        if (!keep) released = true
        return keep
    }
}
