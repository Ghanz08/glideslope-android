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

// x1, y1, x2, y2, alpha of the dashes that carry the path on, fading out (48-unit drawing)
private val DASHES = listOf(
        floatArrayOf(28.6f, 33.34f, 30.2f, 34.62f, 0.95f),
        floatArrayOf(31.4f, 35.58f, 33.0f, 36.86f, 0.79f),
        floatArrayOf(34.2f, 37.82f, 35.8f, 39.1f, 0.63f),
        floatArrayOf(37.0f, 40.06f, 38.6f, 41.34f, 0.47f),
        floatArrayOf(39.8f, 42.3f, 41.4f, 43.58f, 0.31f),
)

/** Logo tile: rounded square with the mark inside (48-unit drawing, its centre at 24 / 26, drawn at 82 % of the tile). */
@Composable
fun GsLogo(size: Dp = 72.dp, bg: Color = Token.aksen, fg: Color = Token.teksDiAksen, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.26f)).background(bg), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val k = this.size.width / 48f * 0.82f
            fun pt(x: Float, y: Float) = Offset(this.size.width / 2f + (x - 24f) * k, this.size.height / 2f + (y - 26f) * k)
            val p = Path().apply {
                val pts = listOf(5f to 7f, 8.84f to 9.5f, 9.09f to 12.5f, 16.16f to 17.5f, 16.4f to 21f, 23.6f to 26f, 23.8f to 29f)
                pts.forEachIndexed { i, (x, y) -> val q = pt(x, y); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }
            }
            drawPath(p, fg, style = Stroke(2.4f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val dotAt = pt(23.8f, 29.3f)
            drawCircle(fg.copy(alpha = 0.45f), 5.6f * k, dotAt, style = Stroke(1.2f * k))
            drawCircle(fg, 3f * k, dotAt)
            DASHES.forEach { d -> drawLine(fg.copy(alpha = d[4]), pt(d[0], d[1]), pt(d[2], d[3]), 1.2f * k, StrokeCap.Round) }
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
