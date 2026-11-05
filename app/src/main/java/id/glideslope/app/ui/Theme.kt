// The PWA's design tokens (glideslope src/app/globals.css) as a fixed Material 3 theme; light only, like the PWA.
// Keep the values in step with globals.css when the tokens change there.
package id.glideslope.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object Token {
    val latar = Color(0xFFF4F6F9)
    val permukaan = Color(0xFFFFFFFF)
    val teks = Color(0xFF0F1A2B)
    val teksPelan = Color(0xFF5F6B7C)
    val isian = Color(0xFFEEF1F5)
    val garis = Color(0xFFE3E8EF)
    val garisKontrol = Color(0xFF7F8CA0)
    val aksen = Color(0xFF2D5BD6)
    val aksenLembut = Color(0xFFE6EBFA)
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
fun GlideslopeTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colors, shapes = shapes, content = content)
