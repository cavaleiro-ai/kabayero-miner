package br.com.cavaleiros.mineradores.data

import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class MiningMode(val label: String, val description: String) {
    SOLO(
        "Solo",
        "Voce compete sozinho: se o seu celular encontrar um bloco, a recompensa inteira (menos a taxa do servidor solo) vai para o seu endereco. Sem pagamentos parciais."
    ),
    POOL(
        "Pool",
        "Voce soma forcas com outros mineradores e recebe uma fracao proporcional aos shares enviados. Pagamentos pequenos e frequentes, direto no seu endereco."
    )
}

enum class UsernameFormat { ADDRESS_DOT_WORKER, ADDRESS_ONLY }

data class PoolPreset(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val tls: Boolean,
    val mode: MiningMode,
    val description: String,
    val usernameFormat: UsernameFormat = UsernameFormat.ADDRESS_DOT_WORKER,
    val minPayoutNote: String = ""
)

object PoolPresets {
    const val CUSTOM_ID = "custom"

    val SOLO: List<PoolPreset> = listOf(
        PoolPreset(
            id = "ckpool_solo",
            name = "solo.ckpool.org",
            host = "solo.ckpool.org",
            port = 3333,
            tls = false,
            mode = MiningMode.SOLO,
            description = "Servidor solo classico. Ao encontrar um bloco, a recompensa vai 98% para o seu endereco (2% de taxa). Usuario = endereco BTC.",
            minPayoutNote = "Pagamento apenas ao encontrar um bloco."
        ),
        PoolPreset(
            id = "public_pool",
            name = "public-pool.io",
            host = "public-pool.io",
            port = 21496,
            tls = false,
            mode = MiningMode.SOLO,
            description = "Servidor solo open-source, sem taxa, popular entre mineradores domesticos (Bitaxe). Usuario = endereco BTC.",
            minPayoutNote = "Pagamento apenas ao encontrar um bloco."
        )
    )

    val POOL: List<PoolPreset> = listOf(
        PoolPreset(
            id = "ocean",
            name = "OCEAN (mine.ocean.xyz)",
            host = "mine.ocean.xyz",
            port = 3334,
            tls = false,
            mode = MiningMode.POOL,
            description = "Pool sem cadastro: o usuario e o seu endereco BTC e os pagamentos saem direto da coinbase do bloco (TIDES).",
            minPayoutNote = "Pagamento quando sua fracao atinge o minimo do pool."
        )
    )

    fun all(): List<PoolPreset> = SOLO + POOL
    fun byId(id: String): PoolPreset? = all().firstOrNull { it.id == id }
}

/**
 * Traducao entre a potencia escolhida pelo usuario (0..100% do aparelho) e o par
 * (threads, intensidade) usado pelo motor. 100% = todos os nucleos o tempo todo;
 * 50% em 4 nucleos = 2 threads a 100%; 30% em 4 nucleos = 2 threads a 60%.
 */
data class CpuLoad(val threads: Int, val intensity: Int) {
    /** Potencia equivalente (0..100) em um aparelho com [cores] nucleos. */
    fun toPower(cores: Int): Int {
        if (cores <= 0 || threads <= 0) return 0
        return (threads.coerceAtMost(cores) * intensity.coerceIn(0, 100).toDouble() / cores).roundToInt().coerceIn(0, 100)
    }

    val label: String
        get() = if (threads <= 0 || intensity <= 0) "em espera" else "$threads thread${if (threads > 1) "s" else ""} a $intensity%"

    companion object {
        fun fromPower(power: Int, cores: Int): CpuLoad {
            val p = power.coerceIn(0, 100)
            val c = cores.coerceAtLeast(1)
            if (p == 0) return CpuLoad(0, 0)
            val load = p / 100.0 * c                                   // nucleos equivalentes
            val threads = ceil(load - 1e-9).toInt().coerceIn(1, c)
            val intensity = (load / threads * 100).roundToInt().coerceIn(1, 100)
            return CpuLoad(threads, intensity)
        }
    }
}

/** Preferencias do minerador (persistidas cifradas). */
data class MinerSettings(
    val mode: MiningMode = MiningMode.SOLO,
    val presetId: String = "ckpool_solo",
    val customHost: String = "",
    val customPort: Int = 3333,
    val customTls: Boolean = false,
    val customLabel: String = "Pool personalizado",
    val workerName: String = "kabayero",
    val password: String = "x",
    /** 0..100: fracao da potencia total do aparelho dedicada a mineracao (ver [CpuLoad]). */
    val power: Int = DEFAULT_POWER,
    val onlyWhileCharging: Boolean = true,
    val minBatteryPercent: Int = 30,
    /** Pausa a mineracao quando a bateria atinge esta temperatura (C). */
    val maxBatteryTempC: Int = 42,
    /** Retoma somente quando a bateria esfriar ate esta temperatura (C). */
    val resumeBatteryTempC: Int = 38,
    val suggestDifficulty: Boolean = true,
    val matrixRain: Boolean = true,
    val appLock: Boolean = false,
    val balanceLookup: Boolean = false
) {
    class ResolvedPool(val label: String, val host: String, val port: Int, val tls: Boolean, val usernameFormat: UsernameFormat)

    fun resolvePool(): ResolvedPool {
        if (presetId == PoolPresets.CUSTOM_ID) {
            return ResolvedPool(customLabel.ifBlank { customHost }, customHost.trim(), customPort, customTls, UsernameFormat.ADDRESS_DOT_WORKER)
        }
        val preset = PoolPresets.byId(presetId)
            ?: (if (mode == MiningMode.SOLO) PoolPresets.SOLO.first() else PoolPresets.POOL.first())
        return ResolvedPool(preset.name, preset.host, preset.port, preset.tls, preset.usernameFormat)
    }

    /** Garante retomar < pausar (pelo menos 1 C de histerese). */
    fun withThermalLimits(pauseC: Int = maxBatteryTempC, resumeC: Int = resumeBatteryTempC): MinerSettings {
        val p = pauseC.coerceIn(MIN_PAUSE_TEMP, MAX_PAUSE_TEMP)
        val r = resumeC.coerceIn(MIN_RESUME_TEMP, p - 1)
        return copy(maxBatteryTempC = p, resumeBatteryTempC = r)
    }

    /** Threads e intensidade que a potencia escolhida representa neste aparelho. */
    fun cpuLoad(): CpuLoad = CpuLoad.fromPower(power, maxThreads())

    fun username(address: String): String {
        val pool = resolvePool()
        val worker = workerName.trim().replace(Regex("[^A-Za-z0-9_-]"), "").take(32)
        return if (pool.usernameFormat == UsernameFormat.ADDRESS_ONLY || worker.isEmpty()) address else "$address.$worker"
    }

    fun toJson(): String = JSONObject()
        .put("mode", mode.name)
        .put("presetId", presetId)
        .put("customHost", customHost)
        .put("customPort", customPort)
        .put("customTls", customTls)
        .put("customLabel", customLabel)
        .put("workerName", workerName)
        .put("password", password)
        .put("power", power)
        .put("onlyWhileCharging", onlyWhileCharging)
        .put("minBatteryPercent", minBatteryPercent)
        .put("maxBatteryTempC", maxBatteryTempC)
        .put("resumeBatteryTempC", resumeBatteryTempC)
        .put("suggestDifficulty", suggestDifficulty)
        .put("matrixRain", matrixRain)
        .put("appLock", appLock)
        .put("balanceLookup", balanceLookup)
        .toString()

    companion object {
        const val DEFAULT_POWER = 50
        const val MIN_PAUSE_TEMP = 35
        const val MAX_PAUSE_TEMP = 60
        const val MIN_RESUME_TEMP = 25
        fun maxThreads(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 32)

        fun fromJson(json: String?): MinerSettings {
            if (json.isNullOrBlank()) return MinerSettings()
            return try {
                val o = JSONObject(json)
                val d = MinerSettings()
                MinerSettings(
                    mode = runCatching { MiningMode.valueOf(o.optString("mode", d.mode.name)) }.getOrDefault(d.mode),
                    presetId = o.optString("presetId", d.presetId),
                    customHost = o.optString("customHost", d.customHost),
                    customPort = o.optInt("customPort", d.customPort),
                    customTls = o.optBoolean("customTls", d.customTls),
                    customLabel = o.optString("customLabel", d.customLabel),
                    workerName = o.optString("workerName", d.workerName),
                    password = o.optString("password", d.password),
                    power = when {
                        o.has("power") -> o.optInt("power", d.power).coerceIn(0, 100)
                        // formato antigo (threads + intensidade) -> potencia equivalente
                        o.has("threads") -> CpuLoad(o.optInt("threads", 1), o.optInt("intensity", 70)).toPower(maxThreads())
                        else -> d.power
                    },
                    onlyWhileCharging = o.optBoolean("onlyWhileCharging", d.onlyWhileCharging),
                    minBatteryPercent = o.optInt("minBatteryPercent", d.minBatteryPercent).coerceIn(0, 100),
                    maxBatteryTempC = o.optInt("maxBatteryTempC", d.maxBatteryTempC).coerceIn(MIN_PAUSE_TEMP, MAX_PAUSE_TEMP),
                    resumeBatteryTempC = o.optInt("resumeBatteryTempC", d.resumeBatteryTempC).coerceIn(MIN_RESUME_TEMP, MAX_PAUSE_TEMP),
                    suggestDifficulty = o.optBoolean("suggestDifficulty", d.suggestDifficulty),
                    matrixRain = o.optBoolean("matrixRain", d.matrixRain),
                    appLock = o.optBoolean("appLock", d.appLock),
                    balanceLookup = o.optBoolean("balanceLookup", d.balanceLookup)
                ).let { it.withThermalLimits() }
            } catch (e: Exception) {
                MinerSettings()
            }
        }
    }
}

enum class WalletSource { GENERATED, IMPORTED }

/** Dados PUBLICOS da carteira. Nenhum segredo e persistido. */
data class WalletInfo(
    val address: String,
    val zpub: String?,
    val masterFingerprint: String?,
    val derivationPath: String?,
    val label: String,
    val createdAt: Long,
    val source: WalletSource
) {
    fun toJson(): String = JSONObject()
        .put("address", address)
        .put("zpub", zpub ?: JSONObject.NULL)
        .put("masterFingerprint", masterFingerprint ?: JSONObject.NULL)
        .put("derivationPath", derivationPath ?: JSONObject.NULL)
        .put("label", label)
        .put("createdAt", createdAt)
        .put("source", source.name)
        .toString()

    companion object {
        fun fromJson(json: String?): WalletInfo? {
            if (json.isNullOrBlank()) return null
            return try {
                val o = JSONObject(json)
                WalletInfo(
                    address = o.getString("address"),
                    zpub = if (o.isNull("zpub")) null else o.optString("zpub"),
                    masterFingerprint = if (o.isNull("masterFingerprint")) null else o.optString("masterFingerprint"),
                    derivationPath = if (o.isNull("derivationPath")) null else o.optString("derivationPath"),
                    label = o.optString("label", "Carteira"),
                    createdAt = o.optLong("createdAt", 0L),
                    source = runCatching { WalletSource.valueOf(o.optString("source")) }.getOrDefault(WalletSource.IMPORTED)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
