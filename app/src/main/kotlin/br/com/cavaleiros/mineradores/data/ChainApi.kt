package br.com.cavaleiros.mineradores.data

import br.com.cavaleiros.mineradores.wallet.Utxo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Acesso a cadeia via mempool.space (HTTPS) para o envio de BTC: saidas nao gastas,
 * taxas recomendadas, altura atual e transmissao da transacao assinada.
 * O endereco e revelado ao provedor; a chave privada nunca sai do aparelho.
 */
object ChainApi {
    private const val BASE = "https://mempool.space/api"

    class Fees(val fastest: Int, val halfHour: Int, val hour: Int, val economy: Int, val minimum: Int)

    suspend fun utxos(address: String): List<Utxo> = withContext(Dispatchers.IO) {
        val arr = JSONArray(request("GET", "/address/${address.trim()}/utxo"))
        val out = ArrayList<Utxo>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val status = o.optJSONObject("status")
            out.add(Utxo(o.getString("txid"), o.getInt("vout"), o.getLong("value"), status?.optBoolean("confirmed", false) ?: false))
        }
        out
    }

    suspend fun fees(): Fees = withContext(Dispatchers.IO) {
        val o = JSONObject(request("GET", "/v1/fees/recommended"))
        Fees(
            fastest = o.optInt("fastestFee", 1).coerceAtLeast(1),
            halfHour = o.optInt("halfHourFee", 1).coerceAtLeast(1),
            hour = o.optInt("hourFee", 1).coerceAtLeast(1),
            economy = o.optInt("economyFee", 1).coerceAtLeast(1),
            minimum = o.optInt("minimumFee", 1).coerceAtLeast(1)
        )
    }

    suspend fun tipHeight(): Int = withContext(Dispatchers.IO) {
        request("GET", "/blocks/tip/height").trim().toInt()
    }

    /** Transmite a transacao (hex) e devolve o txid aceito pelo no. */
    suspend fun broadcast(rawHex: String): String = withContext(Dispatchers.IO) {
        request("POST", "/tx", rawHex).trim()
    }

    private fun request(method: String, path: String, body: String? = null): String {
        val conn = URL(BASE + path).openConnection() as HttpsURLConnection
        try {
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.requestMethod = method
            conn.setRequestProperty("Accept", "application/json, text/plain")
            conn.setRequestProperty("User-Agent", "KabayeroMiner/1.0")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "text/plain")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.US_ASCII)) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = try { conn.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: IOException) { null }
                throw IOException("HTTP $code${if (!err.isNullOrBlank()) ": ${err.take(300)}" else ""}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
