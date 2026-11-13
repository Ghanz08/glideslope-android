package id.glideslope.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiErrorTest {
    @Test fun `legacy shape from auth and sync`() {
        val f = parseFailure(401, """{"error":{"code":"MASUK_GAGAL","message":"Email atau kata sandi salah."}}""")
        assertEquals(401, f.status); assertEquals("MASUK_GAGAL", f.code); assertEquals("Email atau kata sandi salah.", f.message)
    }

    @Test fun `RFC 9457 shape from the layar screens and receipts`() {
        val f = parseFailure(415, """{"${'$'}schema":"x","status":415,"title":"Unsupported Media Type","detail":"Pilih foto JPEG, PNG, atau WebP.","code":"FORMAT"}""")
        assertEquals("FORMAT", f.code); assertEquals("Pilih foto JPEG, PNG, atau WebP.", f.message)
    }

    @Test fun `anything else falls back to a generic message`() {
        val f = parseFailure(502, "<html>bad gateway</html>")
        assertEquals("GALAT", f.code); assertEquals("Server belum bisa dihubungi. Coba lagi.", f.message)
    }
}
