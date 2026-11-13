// F-09 on-device OCR: the text lines ML Kit reads from a receipt photo, in the shape the server's parser expects
// (src/lib/core/receipt-ocr.ts, backend/internal/domain/receipt.go): `{"width":…,"lines":[{"text","score","box":[x0,y0,x1,y1]}]}`.
package id.glideslope.core

data class OcrLine(val text: String, val score: Double, val x0: Double, val y0: Double, val x1: Double, val y1: Double)

private fun esc(s: String) = buildString {
    for (c in s) when {
        c == '"' -> append("\\\"")
        c == '\\' -> append("\\\\")
        c == '\n' -> append("\\n")
        c == '\r' -> append("\\r")
        c == '\t' -> append("\\t")
        c < ' ' -> append("\\u%04x".format(c.code))
        else -> append(c)
    }
}

/**
 * The lines worth sending: blank text and degenerate boxes are dropped, text is capped at 200 characters and the list at
 * 500 lines (the server refuses more). [score] is ML Kit's confidence, or 0.9 where it reports none.
 */
fun ocrJson(width: Int, lines: List<OcrLine>): String? {
    if (width <= 0) return null
    val ok = lines.filter { it.text.isNotBlank() && it.x1 >= it.x0 && it.y1 >= it.y0 }.take(500)
    if (ok.isEmpty()) return null
    return ok.joinToString(",", prefix = "{\"width\":$width,\"lines\":[", postfix = "]}") {
        val score = if (it.score > 0) it.score else 0.9
        "{\"text\":\"${esc(it.text.trim().take(200))}\",\"score\":${"%.3f".format(java.util.Locale.ROOT, score)}," +
            "\"box\":[${"%.1f".format(java.util.Locale.ROOT, it.x0)},${"%.1f".format(java.util.Locale.ROOT, it.y0)},${"%.1f".format(java.util.Locale.ROOT, it.x1)},${"%.1f".format(java.util.Locale.ROOT, it.y1)}]}"
    }
}

/** Longest side → [max] px, never enlarging (the web does the same before upload: 1568 px). */
fun fitSize(w: Int, h: Int, max: Int = 1568): Pair<Int, Int> {
    val s = minOf(1.0, max.toDouble() / maxOf(w, h))
    return Math.round(w * s).toInt() to Math.round(h * s).toInt()
}
