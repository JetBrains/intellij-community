package com.intellij.ide.starter.driver.reused

import com.intellij.driver.client.Driver
import com.intellij.driver.client.utility
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.ExpectedToFail
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

/**
 * Verifies that reused IDE recovery restarts an IDE that stopped during the previous test.
 *
 * The first test of the nested scenario writes a marker file into the shared system directory, and then force-kills
 * its own IDE. Recovery must start a new IDE from the baseline, and the second test must run on it. Recovery never
 * empties the shared system directory, so the marker must stay.
 */
class ReusedModeStoppedIdeRecoveryTest {
  @Test
  fun `second test runs on a new IDE after the previous test stopped the IDE`() {
    StoppedIdeRecoveryScenarioState.reset()

    val summary = executeReusedIdeRecoveryScenario(StoppedIdeRecoveryScenario::class.java)

    assertEquals(2, summary.testsFoundCount)
    assertEquals(0, summary.testsFailedCount)
    assertEquals(1, summary.testsAbortedCount)
    assertEquals(1, summary.testsSucceededCount)
    assertTrue(summary.failures.isEmpty())
    assertEquals(listOf("first", "second"), StoppedIdeRecoveryScenarioState.events)
  }
}

@TestMethodOrder(OrderAnnotation::class)
@ReusedRun
@Disabled("Executed only through ReusedModeStoppedIdeRecoveryTest")
class StoppedIdeRecoveryScenario {
  companion object {
    @ReusedProject
    private val project: CachedProjectConfiguration =
      cachedProjectConfiguration(Files.createTempDirectory("reused-stopped-ide-recovery-project"))
  }

  @Test
  @Order(1)
  @ExpectedToFail
  fun `first test stops the IDE`(driver: Driver, bgRun: BackgroundRun) {
    StoppedIdeRecoveryScenarioState.events += "first"
    StoppedIdeRecoveryScenarioState.ideProcessId = bgRun.process.id
    driver.systemDirMarker().writeText("written before the IDE stopped")

    bgRun.forceKill()
    waitFor("The IDE of the first test is dead", 1.minutes) { !bgRun.process.isAlive }
    throw AssertionError("Expected failure after stopping the IDE")
  }

  @Test
  @Order(2)
  fun `second test runs on a new IDE`(driver: Driver, bgRun: BackgroundRun) {
    StoppedIdeRecoveryScenarioState.events += "second"
    assertNotEquals(StoppedIdeRecoveryScenarioState.ideProcessId, bgRun.process.id)
    assertTrue(bgRun.process.isAlive)
    assertTrue(driver.systemDirMarker().exists(), "Recovery must keep the shared system directory")
  }
}

private fun Driver.systemDirMarker(): Path = Path.of(utility<PathManagerRemote>().getSystemPath()).resolve("reused-stopped-ide-marker.txt")

private object StoppedIdeRecoveryScenarioState {
  var ideProcessId: String? = null
  val events = mutableListOf<String>()

  fun reset() {
    ideProcessId = null
    events.clear()
  }
}
