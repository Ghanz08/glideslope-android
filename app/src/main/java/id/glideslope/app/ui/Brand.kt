// The Glideslope mark (the PWA's blue tile with the trending-down glyph), used on the lock screen and the sign-in page.
package id.glideslope.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GsLogo(size: Dp = 72.dp, bg: Color = Token.aksen, fg: Color = Token.teksDiAksen, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(bg), contentAlignment = Alignment.Center) {
        Icon(Ic.trendingDown, null, tint = fg, modifier = Modifier.size(size * 0.55f))
    }
}
