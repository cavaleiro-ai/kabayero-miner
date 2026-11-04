package br.com.cavaleiros.mineradores.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Consulta opcional de saldo via mempool.space (HTTPS). Desligada por padrao:
 * ao consultar, o endereco e revelado ao provedor da API.
 */
object BalanceApi {
    data class Balance(val confirmedSats: Long, val unconfirmedSats: Long, val txCount: Int) {
        val totalSats: Long get() = confirmedSats + unconfirmedSats
    }

    suspend fun fetch(address: String): Balance = withContext(Dispatchers.IO) {
        val url = URL("https://mempool.space/api/address/${address.trim()}")
        val conn = url.openConnection() as HttpsURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "CavaleirosMineradores/1.0")
            val code = conn.responseCode
            if (code != 200) throw IOException("HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(body)
            val chain = o.getJSONObject("chain_stats")
            val mem = o.getJSONObject("mempool_stats")
            Balance(
                confirmedSats = chain.getLong("funded_txo_sum") - chain.getLong("spent_txo_sum"),
                unconfirmedSats = mem.getLong("funded_txo_sum") - mem.getLong("spent_txo_sum"),
                txCount = chain.getInt("tx_count") + mem.getInt("tx_count")
            )
        } finally {
            conn.disconnect()
        }
    }

    fun formatBtc(sats: Long): String {
        val whole = sats / 100_000_000
        val frac = Math.abs(sats % 100_000_000)
        return String.format("%d.%08d BTC", whole, frac)
    }
}
