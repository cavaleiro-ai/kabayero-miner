package br.com.cavaleiros.mineradores.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import br.com.cavaleiros.mineradores.App
import br.com.cavaleiros.mineradores.MainActivity
import br.com.cavaleiros.mineradores.R
import br.com.cavaleiros.mineradores.data.MinerSettings
import br.com.cavaleiros.mineradores.mining.Miner
import br.com.cavaleiros.mineradores.mining.MiningConfig
import br.com.cavaleiros.mineradores.mining.MiningEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servico em primeiro plano que mantem o processo vivo enquanto minera.
 * Monitora bateria e temperatura e pausa/retoma o motor conforme as preferencias.
 */
class MiningService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var loadJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMining()
                return START_NOT_STICKY
            }
            else -> startMining()
        }
        return START_STICKY
    }

    private fun startMining() {
        val repo = App.repository(this)
        val wallet = repo.wallet.value
        val settings = repo.settings.value
        if (wallet == null) {
            stopSelf()
            return
        }
        val pool = settings.resolvePool()
        if (pool.host.isBlank()) {
            stopSelf()
            return
        }
        val load = settings.cpuLoad()
        val config = MiningConfig(
            host = pool.host,
            port = pool.port,
            tls = pool.tls,
            username = settings.username(wallet.address),
            password = settings.password.ifBlank { "x" },
            threads = load.threads,
            intensity = load.intensity,
            maxThreads = MinerSettings.maxThreads(),
            suggestedDifficulty = if (settings.suggestDifficulty) SUGGESTED_DIFFICULTY else null,
            label = "${settings.mode.label} - ${pool.label}"
        )

        createChannel()
        val notification = buildNotification(Miner.engine.stats.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireWakeLock()
        Miner.engine.start(config)

        monitorJob?.cancel()
        monitorJob = scope.launch { monitorLoop() }
        // barra de potencia: aplica mudancas de threads/intensidade em tempo real
        loadJob?.cancel()
        loadJob = scope.launch {
            repo.settings.collect { s ->
                val l = s.cpuLoad()
                Miner.engine.setLoad(l.threads, l.intensity)
            }
        }
    }

    private fun stopMining() {
        monitorJob?.cancel()
        monitorJob = null
        loadJob?.cancel()
        loadJob = null
        Miner.engine.stop()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Le as configuracoes a cada ciclo, entao mudancas (temperaturas, carregador) valem na hora. */
    private suspend fun monitorLoop() {
        val repo = App.repository(this)
        var tick = 0
        while (scope.isActive && Miner.engine.isRunning) {
            val s = repo.settings.value
            val battery = readBattery()
            if (battery != null) {
                val reason = when {
                    s.onlyWhileCharging && !battery.charging -> "aguardando carregador"
                    battery.percent in 0 until s.minBatteryPercent && !battery.charging -> "bateria abaixo de ${s.minBatteryPercent}%"
                    battery.tempC >= s.maxBatteryTempC -> "temperatura ${battery.tempC}°C (limite ${s.maxBatteryTempC}°C; retoma a ${s.resumeBatteryTempC}°C)"
                    else -> null
                }
                if (reason != null) Miner.engine.pause(reason)
                else if (Miner.engine.stats.value.paused) {
                    // histerese termica definida pelo usuario: so retoma quando esfriar ate a temperatura de retorno
                    val thermal = Miner.engine.stats.value.pauseReason.orEmpty().startsWith("temperatura")
                    if (!thermal || battery.tempC <= s.resumeBatteryTempC) {
                        Miner.engine.resume()
                    }
                }
            }
            if (tick % 2 == 0) updateNotification()
            tick++
            delay(5_000)
        }
        if (!Miner.engine.isRunning) {
            releaseWakeLock()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    data class BatteryState(val percent: Int, val charging: Boolean, val tempC: Int)

    private fun readBattery(): BatteryState? {
        val intent = ContextCompat.registerReceiver(this, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED)
            ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL || plugged != 0
        return BatteryState(percent, charging, temp / 10)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CavaleirosMineradores:mining").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_mining),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_mining_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(stats: br.com.cavaleiros.mineradores.mining.MiningStats): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, MiningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = when {
            stats.paused -> "Pausado: ${stats.pauseReason ?: ""}"
            else -> "${MiningEngine.formatHashrate(stats.hashrate)} | shares ${stats.sharesAccepted}/${stats.sharesSubmitted} | ${stats.connection.label}"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_mining_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setColor(0xFF00FF41.toInt())
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try { nm.notify(NOTIFICATION_ID, buildNotification(Miner.engine.stats.value)) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        if (Miner.engine.isRunning) Miner.engine.stop()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // o servico continua rodando mesmo se o app for removido da lista de recentes
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        const val CHANNEL_ID = "mining"
        const val NOTIFICATION_ID = 7331
        const val ACTION_START = "br.com.cavaleiros.mineradores.action.START"
        const val ACTION_STOP = "br.com.cavaleiros.mineradores.action.STOP"
        /** Sugestao enviada ao pool para receber feedback de shares mais cedo em hardware lento. */
        const val SUGGESTED_DIFFICULTY = 0.001

        fun start(context: Context) {
            val intent = Intent(context, MiningService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MiningService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
