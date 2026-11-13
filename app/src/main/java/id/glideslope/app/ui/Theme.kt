// The PWA's design tokens (glideslope src/app/globals.css) as a Material 3 theme: Terang, Gelap or Sistem (F-16.2).
// Keep the values in step with globals.css when the tokens change there.
package id.glideslope.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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

/** One full set of the PWA's colour tokens (globals.css `@theme` for Terang, `[data-tema="gelap"]` for Gelap). */
class Palette(
    val latar: Color, val permukaan: Color, val permukaanTinggi: Color, val teks: Color, val teksRedup: Color, val teksPelan: Color, val teksDiAksen: Color,
    val isian: Color, val garis: Color, val garisKontrol: Color, val aksen: Color, val aksenTekan: Color, val aksenTeks: Color, val aksenLembut: Color,
    val aksenSedang: Color, val aksenTinggi: Color, val masuk: Color, val masukLatar: Color, val peringatan: Color, val peringatanLatar: Color,
    val galat: Color, val galatLatar: Color, val scrim: Color, val kaca: Color, val kacaGaris: Color, val grafik1: Color, val grafik2: Color,
    val tones: Map<String, Pair<Color, Color>>, // tone_key -> (latar, ikon/teks)
)

private fun tones(vararg t: Triple<String, Long, Long>) = t.associate { (k, bg, fg) -> k to (Color(bg) to Color(fg)) }

val LightPalette = Palette(
    latar = Color(0xFFF4F6F9), permukaan = Color(0xFFFFFFFF), permukaanTinggi = Color(0xFFFFFFFF), teks = Color(0xFF0F1A2B), teksRedup = Color(0xFF4B586B),
    teksPelan = Color(0xFF5F6B7C), teksDiAksen = Color(0xFFFFFFFF), isian = Color(0xFFEEF1F5), garis = Color(0xFFE3E8EF), garisKontrol = Color(0xFF7F8CA0),
    aksen = Color(0xFF2D5BD6), aksenTekan = Color(0xFF2449B0), aksenTeks = Color(0xFF2D5BD6), aksenLembut = Color(0xFFE6EBFA), aksenSedang = Color(0xFFABBDEF),
    aksenTinggi = Color(0xFF6C8CE2), masuk = Color(0xFF0E7A55), masukLatar = Color(0xFFE1F2EA), peringatan = Color(0xFF7D5900), peringatanLatar = Color(0xFFFBF0D2),
    galat = Color(0xFFAE2D49), galatLatar = Color(0xFFFBE5E9), scrim = Color(0x730F1A2B), kaca = Color(0xB8FFFFFF), kacaGaris = Color(0xCCFFFFFF),
    grafik1 = Color(0xFF2D5BD6), grafik2 = Color(0xFFA24E17),
    tones = tones(
        Triple("makan", 0xFFFDEBDD, 0xFFA24E17), Triple("rumah", 0xFFE1F2EA, 0xFF1B7556), Triple("hiburan", 0xFFF0E8FB, 0xFF6F3DB5),
        Triple("belanja", 0xFFE3EDFB, 0xFF2559B0), Triple("pendidikan", 0xFFFBF0D2, 0xFF7D5900), Triple("netral", 0xFFE6EBF2, 0xFF3F4F66),
        Triple("kesehatan", 0xFFFBE5E9, 0xFFAE2D49), Triple("transportasi", 0xFFE0F0F4, 0xFF1D6577), Triple("tagihan", 0xFFEFEAF2, 0xFF69457C),
    ),
)

val DarkPalette = Palette(
    latar = Color(0xFF0C0C0E), permukaan = Color(0xFF18181B), permukaanTinggi = Color(0xFF1F1F23), teks = Color(0xFFF4F4F5), teksRedup = Color(0xFFA1A1AA),
    teksPelan = Color(0xFF93939C), teksDiAksen = Color(0xFFFFFFFF), isian = Color(0xFF27272A), garis = Color(0xFF2E2E33), garisKontrol = Color(0xFF6B6B74),
    aksen = Color(0xFF3B66DC), aksenTekan = Color(0xFF5479E2), aksenTeks = Color(0xFF8EA8F2), aksenLembut = Color(0xFF1E2333), aksenSedang = Color(0xFF2C3A63),
    aksenTinggi = Color(0xFF2E4A9C), masuk = Color(0xFF4CD195), masukLatar = Color(0xFF12291E), peringatan = Color(0xFFF2C14E), peringatanLatar = Color(0xFF2E240F),
    galat = Color(0xFFFF8A8A), galatLatar = Color(0xFF33171A), scrim = Color(0xA6000000), kaca = Color(0xB81C1C1F), kacaGaris = Color(0x14FFFFFF),
    grafik1 = Color(0xFF7C9BF0), grafik2 = Color(0xFFE8955A),
    tones = tones(
        Triple("makan", 0xFF2B1E15, 0xFFF0A26B), Triple("rumah", 0xFF14261E, 0xFF5CC99B), Triple("hiburan", 0xFF241C30, 0xFFB9A0EE),
        Triple("belanja", 0xFF1A2133, 0xFF8EA8F2), Triple("pendidikan", 0xFF2A2312, 0xFFE9C062), Triple("netral", 0xFF26262A, 0xFFB4B4BC),
        Triple("kesehatan", 0xFF2E181C, 0xFFF59AAA), Triple("transportasi", 0xFF13262B, 0xFF6CC3D6), Triple("tagihan", 0xFF262029, 0xFFC8A9DA),
    ),
)

/** The colours every screen reads. [dark] is set once per theme change by [GlideslopeTheme], which re-creates the tree (`key`). */
object Token {
    @Volatile var dark = false
    private val p get() = if (dark) DarkPalette else LightPalette
    val latar get() = p.latar
    val permukaan get() = p.permukaan
    val permukaanTinggi get() = p.permukaanTinggi
    val teks get() = p.teks
    val teksRedup get() = p.teksRedup
    val teksPelan get() = p.teksPelan
    val teksDiAksen get() = p.teksDiAksen
    val isian get() = p.isian
    val garis get() = p.garis
    val garisKontrol get() = p.garisKontrol
    val aksen get() = p.aksen
    val aksenTekan get() = p.aksenTekan
    val aksenTeks get() = p.aksenTeks
    val aksenLembut get() = p.aksenLembut
    val aksenSedang get() = p.aksenSedang
    val aksenTinggi get() = p.aksenTinggi
    val masuk get() = p.masuk
    val masukLatar get() = p.masukLatar
    val peringatan get() = p.peringatan
    val peringatanLatar get() = p.peringatanLatar
    val galat get() = p.galat
    val galatLatar get() = p.galatLatar
    val scrim get() = p.scrim
    val kaca get() = p.kaca
    val kacaGaris get() = p.kacaGaris
    val grafik1 get() = p.grafik1
    val grafik2 get() = p.grafik2

    /** (background, foreground) for a category tone_key; unknown keys fall back to neutral like the PWA. */
    fun tone(key: String?): Pair<Color, Color> = p.tones[key] ?: p.tones.getValue("netral")
}

/** "terang" | "gelap" | "sistem": the PWA's Tema setting, kept per device. */
object ThemeMode {
    var mode by mutableStateOf("sistem")
        private set

    fun load(ctx: Context) {
        mode = ctx.getSharedPreferences("tampilan", Context.MODE_PRIVATE).getString("tema", "sistem") ?: "sistem"
    }

    fun set(ctx: Context, v: String) {
        mode = v
        ctx.getSharedPreferences("tampilan", Context.MODE_PRIVATE).edit().putString("tema", v).apply()
    }
}

private fun scheme(p: Palette, dark: Boolean) = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = p.aksen, onPrimary = p.teksDiAksen, primaryContainer = p.aksenLembut, onPrimaryContainer = p.teks,
    background = p.latar, onBackground = p.teks, surface = p.permukaan, onSurface = p.teks, surfaceVariant = p.isian, onSurfaceVariant = p.teksPelan,
    surfaceContainer = p.permukaan, surfaceContainerLow = p.permukaan, surfaceContainerHigh = p.permukaanTinggi, surfaceContainerHighest = p.permukaanTinggi,
    outline = p.garisKontrol, outlineVariant = p.garis, error = p.galat, onError = p.teksDiAksen, errorContainer = p.galatLatar, onErrorContainer = p.galat,
    scrim = p.scrim,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), // radius-petak-kecil
    small = RoundedCornerShape(12.dp), // radius-petak
    medium = RoundedCornerShape(14.dp), // radius-strip
    large = RoundedCornerShape(20.dp), // radius-kartu
    extraLarge = RoundedCornerShape(28.dp), // radius-lembar
)

@Composable
fun GlideslopeTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    androidx.compose.runtime.remember { ThemeMode.load(ctx); true }
    val dark = when (ThemeMode.mode) { "gelap" -> true; "terang" -> false; else -> isSystemInDarkTheme() }
    Token.dark = dark
    val activity = ctx as? android.app.Activity
    SideEffect { activity?.let { androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView).run { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark } } }
    androidx.compose.runtime.key(dark) {
        MaterialTheme(colorScheme = scheme(if (dark) DarkPalette else LightPalette, dark), shapes = shapes, typography = type, content = content)
    }
}
