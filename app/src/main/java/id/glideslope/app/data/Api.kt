// Client for the server's /api/v1 (F-14, ADR 0005): bearer token, one refresh on 401, typed failures.
package id.glideslope.app.data

import id.glideslope.app.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    /** The server answered with an error (7.2 shape); `code` like BUTUH_KODE, TANPA_SESI, MASUK_GAGAL. */
    data class Fail(val status: Int, val code: String, val message: String) : ApiResult<Nothing>
    /** No answer: offline, DNS, timeout. The caller keeps its data and tries later. */
    data object Offline : ApiResult<Nothing>
}

@Serializable private data class SessionEnvelope(val session: Session)

class Api(private val settings: Settings, private val sessions: SessionStore) {
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    private fun url(path: String) = settings.serverUrl.trimEnd('/') + path

    private suspend fun send(req: Request): Pair<Int, String>? = withContext(Dispatchers.IO) {
        try {
            http.newCall(req).execute().use { it.code to it.body.string() }
        } catch (_: IOException) {
            null
        }
    }

    private fun fail(status: Int, body: String) = parseFailure(status, body)

    private fun post(path: String, body: String, token: String? = null) =
        Request.Builder().url(url(path)).post(body.toRequestBody(jsonType)).apply { token?.let { header("authorization", "Bearer $it") } }.build()

    private suspend fun sessionCall(req: Request): ApiResult<Session> {
        val (status, body) = send(req) ?: return ApiResult.Offline
        if (status != 200) return fail(status, body)
        val s = json.decodeFromString<SessionEnvelope>(body).session
        sessions.set(s)
        return ApiResult.Ok(s)
    }

    suspend fun login(email: String, password: String) =
        sessionCall(post("/api/v1/auth/masuk", json.encodeToString(mapOf("email" to email, "password" to password))))

    suspend fun verifyCode(code: String): ApiResult<Session> {
        val s = sessions.get() ?: return ApiResult.Fail(401, "TANPA_SESI", "Silakan masuk dulu.")
        return sessionCall(post("/api/v1/auth/totp", json.encodeToString(mapOf("refresh_token" to s.refresh_token, "code" to code)), s.access_token))
    }

    private suspend fun refresh(): Boolean {
        val s = sessions.get() ?: return false
        return when (val r = sessionCall(post("/api/v1/auth/segarkan", json.encodeToString(mapOf("refresh_token" to s.refresh_token))))) {
            is ApiResult.Ok -> r.value.aal == "aal2"
            is ApiResult.Fail -> { if (r.status == 401) sessions.set(null); false }
            ApiResult.Offline -> false
        }
    }

    /** An authorised call; a 401 refreshes the token once and retries. */
    private suspend fun authed(build: (String) -> Request): ApiResult<JsonObject> {
        repeat(2) { attempt ->
            val token = sessions.get()?.access_token ?: return ApiResult.Fail(401, "TANPA_SESI", "Silakan masuk dulu.")
            val (status, body) = send(build(token)) ?: return ApiResult.Offline
            if (status == 401 && attempt == 0 && refresh()) return@repeat
            return if (status in 200..299) ApiResult.Ok(json.parseToJsonElement(body).jsonObject) else fail(status, body)
        }
        return ApiResult.Fail(401, "TANPA_SESI", "Sesi berakhir. Masuk lagi.")
    }

    suspend fun pull(xid: String, seq: Long) =
        authed { Request.Builder().url(url("/api/v1/sync/pull?xid=$xid&seq=$seq&limit=500")).header("authorization", "Bearer $it").build() }

    /** F-14.2: one batch of the outbox; each op's payload is already JSON. */
    suspend fun push(ops: List<OutboxEntity>): ApiResult<JsonObject> {
        val body = "{\"ops\":[" + ops.joinToString(",") { "{\"op_id\":\"${it.opId}\",\"type\":\"${it.type}\",\"payload\":${it.payload}}" } + "]}"
        return authed { post("/api/v1/sync/push", body, it) }
    }

    suspend fun summary() = authed { Request.Builder().url(url("/api/v1/summary")).header("authorization", "Bearer $it").build() }

    suspend fun reportError(where: String, message: String) =
        authed { post("/api/v1/errors", json.encodeToString(mapOf("where" to where, "message" to message.take(2000))), it) }

    /** Any authorised call whose answer may be an object or an array (the layar screens). */
    private suspend fun authedAny(build: (String) -> Request): ApiResult<JsonElement> {
        repeat(2) { attempt ->
            val token = sessions.get()?.access_token ?: return ApiResult.Fail(401, "TANPA_SESI", "Silakan masuk dulu.")
            val (status, body) = send(build(token)) ?: return ApiResult.Offline
            if (status == 401 && attempt == 0 && refresh()) return@repeat
            return if (status in 200..299) ApiResult.Ok(if (body.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(body)) else fail(status, body)
        }
        return ApiResult.Fail(401, "TANPA_SESI", "Sesi berakhir. Masuk lagi.")
    }

    /** GET /api/v1/… (screens under /layar, lists, quotes). */
    suspend fun get(path: String) = authedAny { Request.Builder().url(url(path)).header("authorization", "Bearer $it").build() }

    /** A write to the server (POST, PATCH, PUT, DELETE) with a JSON body; online only, nothing is queued. */
    suspend fun send(method: String, path: String, body: String = "{}") =
        authedAny { Request.Builder().url(url(path)).method(method, if (method == "DELETE" && body == "{}") null else body.toRequestBody(jsonType)).header("authorization", "Bearer $it").build() }

    /**
     * F-09: the receipt photo, plus the text the phone already read (ML Kit) as an optional "ocr" part. A receipt that
     * parses and validates from that text never goes to the OCR service; otherwise the server reads the photo itself.
     */
    suspend fun uploadReceipt(jpeg: ByteArray, ocrJson: String?): ApiResult<JsonElement> {
        val form = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", "nota.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
            .apply { if (ocrJson != null) addFormDataPart("ocr", ocrJson) }
            .build()
        return authedAny { Request.Builder().url(url("/api/v1/receipts")).post(form).header("authorization", "Bearer $it").build() }
    }

    /** F-17: a bank statement (.xlsx or .pdf) and its password, read on the server; also used to re-read for another account. */
    suspend fun previewStatement(bytes: ByteArray, name: String, mime: String, password: String?, accountId: String?): ApiResult<JsonElement> {
        val form = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", name, bytes.toRequestBody(mime.toMediaType()))
            .apply {
                if (!password.isNullOrEmpty()) addFormDataPart("password", password)
                if (accountId != null) addFormDataPart("account_id", accountId)
            }.build()
        return authedAny { Request.Builder().url(url("/api/v1/statements/preview")).post(form).header("authorization", "Bearer $it").build() }
    }

    /**
     * K-22: the photo of a nota, fetched when it is opened and never cached. The API answers 302 to a signed URL that lives 60 seconds;
     * that second request goes out without the bearer token (the URL carries its own).
     */
    suspend fun receiptImage(id: String): ApiResult<ByteArray> {
        val token = sessions.get()?.access_token ?: return ApiResult.Fail(401, "TANPA_SESI", "Silakan masuk dulu.")
        val manual = http.newBuilder().followRedirects(false).build()
        return withContext(Dispatchers.IO) {
            try {
                manual.newCall(Request.Builder().url(url("/api/v1/receipts/$id/image")).header("authorization", "Bearer $token").build()).execute().use { first ->
                    val target = first.header("location")?.let { first.request.url.resolve(it) }
                        ?: return@withContext ApiResult.Fail(first.code, "TANPA_FOTO", "Nota ini belum punya foto.")
                    http.newCall(Request.Builder().url(target).build()).execute().use { second ->
                        if (second.isSuccessful) ApiResult.Ok(second.body.bytes()) else ApiResult.Fail(second.code, "FOTO_GAGAL", "Foto belum bisa diambil. Coba lagi.")
                    }
                }
            } catch (_: IOException) {
                ApiResult.Offline
            }
        }
    }

    /** A POST whose answer is a file (the export ZIP). */
    suspend fun download(path: String, body: String): ApiResult<ByteArray> {
        repeat(2) { attempt ->
            val token = sessions.get()?.access_token ?: return ApiResult.Fail(401, "TANPA_SESI", "Silakan masuk dulu.")
            val res = withContext(Dispatchers.IO) {
                try { http.newCall(post(path, body, token)).execute().use { Triple(it.code, it.body.bytes(), it.header("content-type").orEmpty()) } } catch (_: IOException) { null }
            } ?: return ApiResult.Offline
            if (res.first == 401 && attempt == 0 && refresh()) return@repeat
            return if (res.first in 200..299) ApiResult.Ok(res.second) else fail(res.first, String(res.second))
        }
        return ApiResult.Fail(401, "TANPA_SESI", "Sesi berakhir. Masuk lagi.")
    }

    /**
     * F-01.2: the server checks the PIN (and counts the wrong ones). A 401 here is the answer, not an expired token, so unlike
     * every other call it is never retried: a retry would send the wrong PIN twice and burn two of the five tries.
     * Only a 401 that is not about the PIN (an expired access token) refreshes the token and sends it once more.
     */
    suspend fun verifyPin(pin: String): ApiResult<Unit> {
        repeat(2) { attempt ->
            val token = sessions.get()?.access_token ?: return ApiResult.Fail(401, "TANPA_SESI", "Silakan masuk dulu.")
            val (status, body) = send(post("/api/v1/pin/verify", json.encodeToString(mapOf("pin" to pin)), token)) ?: return ApiResult.Offline
            if (status in 200..299) return ApiResult.Ok(Unit)
            val failure = fail(status, body)
            if (status == 401 && failure.code != "PIN_SALAH" && failure.code != "SESI_BERAKHIR" && attempt == 0 && refresh()) return@repeat
            return failure
        }
        return ApiResult.Fail(401, "TANPA_SESI", "Sesi berakhir. Masuk lagi.")
    }

    suspend fun logout() {
        sessions.get()?.let { send(post("/api/v1/auth/keluar", json.encodeToString(mapOf("refresh_token" to it.refresh_token)), it.access_token)) }
        sessions.set(null)
    }
}

/** The server answers errors in two shapes: legacy `{error:{code,message}}` (auth, sync) and RFC 9457 `{code,detail}` (everything else). */
fun parseFailure(status: Int, body: String): ApiResult.Fail {
    val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
    val legacy = root?.get("error") as? JsonObject
    val code = legacy?.get("code") ?: root?.get("code")
    val msg = legacy?.get("message") ?: root?.get("detail")
    return ApiResult.Fail(
        status,
        (code as? JsonPrimitive)?.content ?: "GALAT",
        (msg as? JsonPrimitive)?.content ?: "Server belum bisa dihubungi. Coba lagi.",
    )
}
