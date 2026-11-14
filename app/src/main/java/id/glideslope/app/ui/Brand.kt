// The Glideslope mark: a stepped glide path down to today's dot, then the dashed way ahead. Same drawing as the sign-in
// page's brand panel (F-16). Used for the logo tile and, large and animated, on the lock screen.
package id.glideslope.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Logo tile: rounded square with the mark inside (48-unit drawing, 34 wide, centred). */
@Composable
fun GsLogo(size: Dp = 72.dp, bg: Color = Token.aksen, fg: Color = Token.teksDiAksen, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.26f)).background(bg), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val k = this.size.width / 48f
            val w = 3f * k
            val p = Path().apply {
                moveTo(7f * k, 8f * k); lineTo(12.3f * k, 11f * k); lineTo(12.6f * k, 14.6f * k); lineTo(22.3f * k, 20.6f * k)
                lineTo(22.6f * k, 24.8f * k); lineTo(32.5f * k, 30.8f * k); lineTo(32.8f * k, 34.4f * k)
            }
            drawPath(p, fg, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(fg, 2.6f * k, Offset(32.8f * k, 35.3f * k))
            drawLine(fg, Offset(35.8f * k, 36.4f * k), Offset(37.4f * k, 37.6f * k), 2.6f * k, StrokeCap.Round)
            drawLine(fg, Offset(39f * k, 38.8f * k), Offset(40.6f * k, 40f * k), 2.6f * k, StrokeCap.Round)
        }
    }
}

/**
 * The sign-in page's descent line, full width: drawn in 500 x 140 units. [progress] 0..1 draws the solid path;
 * the dot and the dashed projection follow it; [pulse] 0..1 is the ring that breathes around the dot.
 */
@Composable
fun GlideLine(color: Color, progress: Float, pulse: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sx = size.width / 500f
        val sy = size.height / 140f
        val unit = size.width / 500f
        val solid = Path().apply {
            moveTo(0f, 10f * sy); lineTo(62f * sx, 20f * sy); lineTo(66f * sx, 32f * sy); lineTo(180f * sx, 52f * sy)
            lineTo(184f * sx, 66f * sy); lineTo(300f * sx, 86f * sy); lineTo(304f * sx, 98f * sy)
        }
        val measure = PathMeasure().apply { setPath(solid, false) }
        val drawn = Path()
        measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), drawn, true)
        drawPath(drawn, color, style = Stroke(3.2f * unit * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (progress >= 1f) {
            val dot = Offset(304f * sx, 99f * sy)
            drawLine(color.copy(alpha = 0.85f), Offset(310f * sx, 101f * sy), Offset(495f * sx, 138f * sy), 2.8f * unit * 1.4f, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(7f * unit * 1.4f, 9f * unit * 1.4f)))
            drawCircle(color.copy(alpha = 0.35f * (1f - pulse)), (7f + 16f * pulse) * unit * 1.4f, dot)
            drawCircle(color, 7f * unit * 1.4f, dot)
        }
    }
}
