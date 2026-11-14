// Keeps the splash screen up while the app starts: at least long enough to see it, and, when signed in, until the first
// pull of data has finished (or [MAX] ms have passed, so a slow network never traps the owner on the splash).
package id.glideslope.app

import android.os.SystemClock

object Boot {
    private const val MIN = 1400L
    private const val MAX = 4500L
    private var start = SystemClock.elapsedRealtime()
    @Volatile var dataReady = false

    fun begin(needsData: Boolean) {
        start = SystemClock.elapsedRealtime()
        dataReady = !needsData
    }

    fun keepSplash(): Boolean {
        val t = SystemClock.elapsedRealtime() - start
        return t < MIN || (!dataReady && t < MAX)
    }
}
