package com.intellij.ide.starter.driver.reused

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.utility
import com.intellij.driver.sdk.saveSettings
import com.intellij.driver.sdk.ui.settings.getCurrentUiSettings
import com.intellij.driver.sdk.ui.settings.updateUiSettings
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.events.IdeAfterLaunchEvent
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.tools.ide.starter.bus.EventsBus
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.ExpectedToFail
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Verifies that reused IDE recovery handles a non-responsive IDE after an expected failed test.
 *
 * The nested scenario marks the first test as [ExpectedToFail], changes a real persisted setting, then blocks
 * the IDE EDT and fails intentionally so the test is aborted as expected. Recovery must kill and restart the
 * IDE, restore the settings files, and let the second test run against a responsive IDE with default runtime
 * settings. The replacement IDE must start only after the killed IDE has finished its post-processing.
 */
class ReusedModeNonResponsiveRecoveryTest {
  @Test
  fun `second test recovers after previous test blocks edt and fails`() {
    NonResponsiveRecoveryScenarioState.reset()
    val runContextsByPid = ConcurrentHashMap<String, IDERunContext>()
    val timeline = CopyOnWriteArrayList<Pair<String, IDERunContext>>()
    val subscriber = Any()
    EventsBus.subscribe(subscriber) { event: IdeLaunchEvent ->
      runContextsByPid[event.ideProcess.id] = event.runContext
      timeline += LAUNCH to event.runContext
    }
    EventsBus.subscribe(subscriber) { event: IdeAfterLaunchEvent ->
      val killedPid = NonResponsiveRecoveryScenarioState.ideProcessId
      if (killedPid != null && runContextsByPid[killedPid] === event.runContext) {
        // Longer than a restart, so a replacement that does not wait for the killed run starts inside this delay.
        delay(KILLED_RUN_POST_PROCESSING_DELAY)
      }
      timeline += POST_PROCESSING_FINISHED to event.runContext
    }

    val summary = try {
      executeReusedIdeRecoveryScenario(NonResponsiveRecoveryScenario::class.java)
    }
    finally {
      EventsBus.unsubscribe<IdeLaunchEvent>(subscriber)
      EventsBus.unsubscribe<IdeAfterLaunchEvent>(subscriber)
    }

    assertEquals(2, summary.testsFoundCount)
    assertEquals(0, summary.testsFailedCount)
    assertEquals(1, summary.testsAbortedCount)
    assertEquals(1, summary.testsSucceededCount)
    assertTrue(summary.failures.isEmpty())
    assertEquals(listOf("first", "second"), NonResponsiveRecoveryScenarioState.events)
    assertInitialAndRestartedIdeaLogsSaved(checkNotNull(NonResponsiveRecoveryScenarioState.testHome))

    val killedRunContext = checkNotNull(runContextsByPid[checkNotNull(NonResponsiveRecoveryScenarioState.ideProcessId)])
    val killedPostProcessing = timeline.indexOfFirst { (kind, runContext) -> kind == POST_PROCESSING_FINISHED && runContext === killedRunContext }
    val replacementLaunch = timeline.indexOfFirst { (kind, runContext) -> kind == LAUNCH && runContext !== killedRunContext }
    assertTrue(killedPostProcessing in 0..<replacementLaunch,
               "Expected the killed IDE to finish post-processing before the replacement launches. Events: ${timeline.map { it.first }}")
  }
}

private const val LAUNCH = "launch"
private const val POST_PROCESSING_FINISHED = "post-processing finished"
private val KILLED_RUN_POST_PROCESSING_DELAY = 10.seconds

@TestMethodOrder(OrderAnnotation::class)
@ReusedRun
@Disabled("Executed only through ReusedIdeRunIdeModeNonResponsiveRecoveryTest")
class NonResponsiveRecoveryScenario {
  companion object {
    @ReusedProject
    private val project: CachedProjectConfiguration =
      cachedProjectConfiguration(Files.createTempDirectory("reused-non-responsive-recovery-project"))
  }

  @BeforeEach
  fun rememberActiveSettingsFile(driver: Driver) {
    val configDir = Path.of(driver.utility<PathManagerRemote>().getConfigPath())
    NonResponsiveRecoveryScenarioState.testHome = configDir.parent
    NonResponsiveRecoveryScenarioState.uiSettingsFile = configDir.resolve("options/ui.lnf.xml")
  }

  @Test
  @Order(1)
  @ExpectedToFail
  fun `first test changes settings and blocks edt`(driver: Driver, bgRun: BackgroundRun) {
    NonResponsiveRecoveryScenarioState.events += "first"
    NonResponsiveRecoveryScenarioState.ideProcessId = bgRun.process.id
    assertTrue(bgRun.process.isAlive)

    val settingsFile = checkNotNull(NonResponsiveRecoveryScenarioState.uiSettingsFile)
    assertFalse(settingsFile.exists())
    assertEquals(NON_RESPONSIVE_DEFAULT_RECENT_FILES_LIMIT, driver.getCurrentUiSettings().recentFilesLimit)

    driver.updateUiSettings {
      recentFilesLimit = NON_RESPONSIVE_CHANGED_RECENT_FILES_LIMIT
    }
    driver.saveSettings()

    assertEquals(NON_RESPONSIVE_CHANGED_RECENT_FILES_LIMIT, driver.getCurrentUiSettings().recentFilesLimit)
    assertTrue(settingsFile.exists())
    val settingsText = settingsFile.readText()
    assertTrue(settingsText.contains("RECENT_FILES_LIMIT"))
    assertTrue(settingsText.contains("""value="$NON_RESPONSIVE_CHANGED_RECENT_FILES_LIMIT""""))

    driver.utility<DriverTestApi>().blockEdt(NON_RESPONSIVE_EDT_BLOCK_TIMEOUT.inWholeMilliseconds)
    throw AssertionError("Expected failure after blocking IDE EDT")
  }

  @Test
  @Order(2)
  fun `second test sees recovered settings after edt was blocked`(driver: Driver, bgRun: BackgroundRun) {
    NonResponsiveRecoveryScenarioState.events += "second"
    assertNotEquals(NonResponsiveRecoveryScenarioState.ideProcessId, bgRun.process.id)
    assertTrue(bgRun.process.isAlive)

    val settingsFile = checkNotNull(NonResponsiveRecoveryScenarioState.uiSettingsFile)
    assertFalse(settingsFile.exists())
    assertEquals(NON_RESPONSIVE_DEFAULT_RECENT_FILES_LIMIT, driver.getCurrentUiSettings().recentFilesLimit)
  }
}

private object NonResponsiveRecoveryScenarioState {
  var testHome: Path? = null
  var ideProcessId: String? = null
  var uiSettingsFile: Path? = null
  val events = mutableListOf<String>()

  fun reset() {
    testHome = null
    ideProcessId = null
    uiSettingsFile = null
    events.clear()
  }
}

private const val NON_RESPONSIVE_DEFAULT_RECENT_FILES_LIMIT = 50
private const val NON_RESPONSIVE_CHANGED_RECENT_FILES_LIMIT = 7
private val NON_RESPONSIVE_EDT_BLOCK_TIMEOUT = 5.minutes

@Remote("com.intellij.driver.impl.DriverTestApi")
private interface DriverTestApi {
  fun blockEdt(millis: Long)
}
