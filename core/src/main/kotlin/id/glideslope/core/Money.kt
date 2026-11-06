// Money is an integer number of rupiah (PRD section 11); shown the way the PWA shows it.
package id.glideslope.core

/** 1234567 → "Rp 1.234.567"; negatives keep the sign in front: "−Rp 5.000". */
fun formatRupiah(amount: Long): String {
    val digits = kotlin.math.abs(amount).toString().reversed().chunked(3).joinToString(".").reversed()
    return (if (amount < 0) "−" else "") + "Rp " + digits
}
