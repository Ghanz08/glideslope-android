// The PWA's design tokens (glideslope src/app/globals.css) as a fixed Material 3 theme; light only, like the PWA.
// Keep the values in step with globals.css when the tokens change there.
package id.glideslope.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import id.glideslope.app.R

val Jakarta = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(R.font.plus_jakarta_sans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

/** PRD 4.2 type scale (mobile). */
private val type = Typography().run {
    fun TextStyle.j(size: Int, line: Int, w: Int = 400) = copy(fontFamily = Jakarta, fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(w))
    copy(
        displaySmall = displaySmall.j(38, 46, 700).copy(letterSpacing = (-0.03).em),
        headlineSmall = headlineSmall.j(24, 30, 700),
        titleLarge = titleLarge.j(20, 26, 700),
        titleMedium = titleMedium.j(15, 20, 700),
        bodyLarge = bodyLarge.j(15, 22),
        bodyMedium = bodyMedium.j(14, 20),
        bodySmall = bodySmall.j(13, 18),
        labelLarge = labelLarge.j(15, 20, 600),
        labelMedium = labelMedium.j(13, 18, 600),
        labelSmall = labelSmall.j(12, 16, 500),
    )
}

object Token {
    val latar = Color(0xFFF4F6F9)
    val permukaan = Color(0xFFFFFFFF)
    val teks = Color(0xFF0F1A2B)
    val teksRedup = Color(0xFF4B586B)
    val teksPelan = Color(0xFF5F6B7C)
    val isian = Color(0xFFEEF1F5)
    val garis = Color(0xFFE3E8EF)
    val garisKontrol = Color(0xFF7F8CA0)
    val aksen = Color(0xFF2D5BD6)
    val aksenLembut = Color(0xFFE6EBFA)
    val aksenSedang = Color(0xFFABBDEF) // color-mix(#2d5bd6 40%, white)
    val aksenTinggi = Color(0xFF6C8CE2) // color-mix(#2d5bd6 70%, white)
    val masuk = Color(0xFF0E7A55)
    val peringatan = Color(0xFF7D5900)
    val peringatanLatar = Color(0xFFFBF0D2)
    val galat = Color(0xFFAE2D49)
    val galatLatar = Color(0xFFFBE5E9)
}

private val colors = lightColorScheme(
    primary = Token.aksen,
    onPrimary = Token.permukaan,
    primaryContainer = Token.aksenLembut,
    onPrimaryContainer = Token.teks,
    background = Token.latar,
    onBackground = Token.teks,
    surface = Token.permukaan,
    onSurface = Token.teks,
    surfaceVariant = Token.isian,
    onSurfaceVariant = Token.teksPelan,
    surfaceContainer = Token.permukaan,
    surfaceContainerLow = Token.permukaan,
    outline = Token.garisKontrol,
    outlineVariant = Token.garis,
    error = Token.galat,
    onError = Token.permukaan,
    errorContainer = Token.galatLatar,
    onErrorContainer = Token.galat,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), // radius-petak-kecil
    small = RoundedCornerShape(12.dp), // radius-petak
    medium = RoundedCornerShape(14.dp), // radius-strip
    large = RoundedCornerShape(20.dp), // radius-kartu
    extraLarge = RoundedCornerShape(28.dp), // radius-lembar
)

@Composable
fun GlideslopeTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colors, shapes = shapes, typography = type, content = content)
