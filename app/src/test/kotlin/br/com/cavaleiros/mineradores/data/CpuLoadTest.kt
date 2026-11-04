package br.com.cavaleiros.mineradores.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuLoadTest {

    @Test
    fun zeroPowerMeansIdle() {
        assertEquals(CpuLoad(0, 0), CpuLoad.fromPower(0, 8))
        assertEquals("em espera", CpuLoad.fromPower(0, 8).label)
    }

    @Test
    fun fullPowerUsesEveryCoreAllTheTime() {
        assertEquals(CpuLoad(4, 100), CpuLoad.fromPower(100, 4))
        assertEquals(CpuLoad(8, 100), CpuLoad.fromPower(100, 8))
        assertEquals(CpuLoad(1, 100), CpuLoad.fromPower(100, 1))
    }

    @Test
    fun partialPowerSplitsIntoWholeThreadsAndDutyCycle() {
        assertEquals(CpuLoad(2, 100), CpuLoad.fromPower(50, 4))   // 2 nucleos inteiros
        assertEquals(CpuLoad(2, 60), CpuLoad.fromPower(30, 4))    // 1.2 nucleos -> 2 threads a 60%
        assertEquals(CpuLoad(1, 40), CpuLoad.fromPower(5, 8))     // 0.4 nucleo -> 1 thread a 40%
        assertEquals(CpuLoad(3, 88), CpuLoad.fromPower(33, 8))    // 2.64 nucleos -> 3 threads a 88%
    }

    @Test
    fun roundTripPreservesPowerForMultiplesOfFive() {
        for (cores in listOf(1, 2, 4, 6, 8, 12)) {
            for (power in 0..100 step 5) {
                val load = CpuLoad.fromPower(power, cores)
                assertEquals("cores=$cores power=$power", power, load.toPower(cores))
                assertTrue(load.threads in 0..cores)
                assertTrue(load.intensity in 0..100)
            }
        }
    }

    @Test
    fun loadIsMonotonicInPower() {
        for (cores in listOf(2, 4, 8)) {
            var last = 0.0
            for (power in 0..100) {
                val load = CpuLoad.fromPower(power, cores)
                val effective = load.threads * load.intensity / 100.0
                assertTrue("cores=$cores power=$power", effective + 1e-9 >= last)
                last = effective
            }
        }
    }

    @Test
    fun legacyThreadsAndIntensityConvertToPower() {
        // formato antigo salvo: 3 threads a 70% em 4 nucleos = 52.5% -> 53
        assertEquals(53, CpuLoad(3, 70).toPower(4))
        assertEquals(100, CpuLoad(16, 100).toPower(8)) // threads acima dos nucleos saturam em 100
        assertEquals(0, CpuLoad(0, 100).toPower(8))
    }

    @Test
    fun settingsRoundTripThroughJson() {
        val s = MinerSettings(power = 35)
        val back = MinerSettings.fromJson(s.toJson())
        assertEquals(35, back.power)
        val legacy = MinerSettings.fromJson("""{"threads":2,"intensity":100}""")
        assertEquals(CpuLoad(2, 100).toPower(MinerSettings.maxThreads()), legacy.power)
        assertEquals(MinerSettings.DEFAULT_POWER, MinerSettings.fromJson("{}").power)
    }
}
