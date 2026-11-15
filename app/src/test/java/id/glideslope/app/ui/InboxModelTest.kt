package id.glideslope.app.ui

import id.glideslope.app.data.TxEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxModelTest {
    private fun tx(id: String, reason: String = "merchant_baru") =
        """{"id":"$id","merchant_raw":"Bunga Jago","merchant_display_name":"Bunga Jago","amount":127708,"direction":"in","kind":"income","occurred_at":"2026-11-12T01:00:00Z","account_id":"a1","review_reason":"$reason","evidence_roles":[]}"""

    private fun item(key: String, ids: List<String>, tx: String, proposals: String = "[]") =
        """{"key":"$key","tx":$tx,"proposals":$proposals,"bucket":[],"groupCount":${ids.size},"groupIds":[${ids.joinToString(",") { "\"$it\"" }}]}"""

    private fun entity(id: String) = TxEntity(id, "a1", "in", 1, "2026-11-12T01:00:00Z", "income", "needs_review", null, "x", null, false, null)

    private fun snapshot(vararg items: String) = """{"items":[${items.joinToString(",")}]}"""

    @Test fun `eight rows of one new merchant are one card`() {
        val ids = (1..8).map { "t$it" }
        val cards = buildCards(snapshot(item("t1", ids, tx("t1"))), ids.toSet(), emptyList(), emptyMap(), emptySet())
        assertEquals(1, cards.size)
        assertEquals(8, cards[0].ids.size)
        assertEquals("Merchant baru · 8 transaksi", cards[0].subtitle)
    }

    @Test fun `a card disappears once the phone confirmed all its rows`() {
        val cards = buildCards(snapshot(item("t1", listOf("t1"), tx("t1", reason = "nominal_besar"))), emptySet(), emptyList(), emptyMap(), emptySet())
        assertTrue(cards.isEmpty())
    }

    @Test fun `a group shrinks as its rows are confirmed`() {
        val cards = buildCards(snapshot(item("t1", listOf("t1", "t2", "t3"), tx("t1"))), setOf("t2", "t3"), emptyList(), emptyMap(), emptySet())
        assertEquals(listOf("t2", "t3"), cards.single().ids)
        assertEquals("Merchant baru · 2 transaksi", cards.single().subtitle)
    }

    @Test fun `a suggestion on a confirmed row stays until it is settled`() {
        val p = """[{"id":"p1","type":"repayment","contact":{"name":"Azril"},"receivable":{"remaining":50000}}]"""
        val snap = snapshot(item("t1", listOf("t1"), tx("t1", reason = "transfer_tidak_jelas"), p))
        val open = buildCards(snap, emptySet(), emptyList(), emptyMap(), emptySet())
        assertEquals("Cicilan dari Azril?", open.single().subtitle)
        assertTrue(buildCards(snap, emptySet(), emptyList(), emptyMap(), setOf("p1")).isEmpty())
    }

    @Test fun `a rejected suggestion leaves a plain review card`() {
        val p = """[{"id":"p1","type":"repayment"}]"""
        val snap = snapshot(item("t1", listOf("t1"), tx("t1", reason = "nominal_besar"), p))
        val c = buildCards(snap, setOf("t1"), emptyList(), emptyMap(), setOf("p1")).single()
        assertTrue(c.proposals.isEmpty())
        assertEquals("Nominal besar, periksa dulu", c.subtitle)
    }

    @Test fun `the server order is kept and unknown local rows come after`() {
        val snap = snapshot(item("b", listOf("b"), tx("b")), item("a", listOf("a"), tx("a")))
        val cards = buildCards(snap, setOf("a", "b", "z"), listOf(entity("z")), mapOf("z" to "kepercayaan_rendah"), emptySet())
        assertEquals(listOf("b", "a", "z"), cards.map { it.key })
    }

    @Test fun `without a server list the local review rows are the queue`() {
        val cards = buildCards(null, setOf("x", "y"), listOf(entity("x"), entity("y")), emptyMap(), emptySet())
        assertEquals(listOf("x", "y"), cards.map { it.key })
    }

    @Test
    fun `unlinked receipts are read from the snapshot and ignored when absent or malformed`() {
        val snap = """{"items":[],"receipts_unlinked":[{"id":"r1","merchant_name":"Nota Uji","total":31000,"has_image":false}]}"""
        assertEquals(listOf("r1"), unlinkedReceipts(snap).map { it.text("id") })
        assertEquals(emptyList<String>(), unlinkedReceipts("""{"items":[]}""").map { it.text("id") })
        assertEquals(emptyList<String>(), unlinkedReceipts("bukan json").map { it.text("id") })
        assertEquals(emptyList<String>(), unlinkedReceipts(null).map { it.text("id") })
    }
}
