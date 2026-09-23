package com.intellij.ide.starter.driver.reused

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ideLogger
import com.intellij.driver.sdk.jdk.getSystemProperty
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.DriverWithContextError
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.seconds

/**
 * Verifies that methods sharing the same reused IDE process still write diagnostics to method-specific log directories.
 */
@TestMethodOrder(OrderAnnotation::class)
@ReusedRun
class ReusedModeSplitLogsTest {
  companion object {
    @delegate:ReusedProject
    val project: CachedProjectConfiguration by lazy {
      cachedProjectConfiguration(Files.createTempDirectory("reused-split-logs-project"))
    }
  }

  @Test
  @Order(1)
  fun `first test writes to its own log`(driver: Driver, bgRun: BackgroundRun) {
    SplitLogsState.processId = bgRun.process.id
    SplitLogsState.firstLog = currentLogFile(driver)
    SplitLogsState.firstScreenshot = assertDriverDiagnosticsSavedUnderCurrentLog(driver)
    driver.ideLogger.info(FIRST_LOG_MARKER)
  }

  @Test
  @Order(2)
  fun `second test writes to a separate log`(driver: Driver, bgRun: BackgroundRun) {
    assertEquals(SplitLogsState.processId, bgRun.process.id)
    val firstLog = checkNotNull(SplitLogsState.firstLog)
    val firstScreenshot = checkNotNull(SplitLogsState.firstScreenshot)
    val secondLog = currentLogFile(driver)
    val secondScreenshot = assertDriverDiagnosticsSavedUnderCurrentLog(driver)
    driver.ideLogger.info(SECOND_LOG_MARKER)

    waitForLogText(firstLog, FIRST_LOG_MARKER)
    waitForLogText(secondLog, SECOND_LOG_MARKER)

    assertFalse(firstLog.readText().contains(SECOND_LOG_MARKER))
    assertFalse(secondLog.readText().contains(FIRST_LOG_MARKER))
    val firstLogDir = firstLog.parent.toAbsolutePath().normalize()
    val secondLogDir = secondLog.parent.toAbsolutePath().normalize()
    assertNotEquals(firstLogDir, secondLogDir)
    assertTrue(firstScreenshot.startsWith(firstLogDir))
    assertTrue(secondScreenshot.startsWith(secondLogDir))
    assertFalse(firstScreenshot.startsWith(secondLogDir))
    assertFalse(secondScreenshot.startsWith(firstLogDir))
  }
}

fun assertDriverDiagnosticsSavedUnderCurrentLog(driver: Driver): Path {
  val currentLogDir = currentLogDir(driver)
  val screenshot = driver.takeScreenshot("splitArtifactsProbe")
  assertNotNull(screenshot)
  val screenshotPath = Path.of(screenshot!!).toAbsolutePath().normalize()
  assertTrue(screenshotPath.exists(), "Screenshot does not exist: $screenshotPath")
  assertTrue(screenshotPath.startsWith(currentLogDir), "Screenshot $screenshotPath is not under $currentLogDir")

  assertThrows(DriverWithContextError::class.java) {
    driver.withContext {
      error("split artifacts diagnostic probe")
    }
  }
  val hierarchy = currentLogDir.resolve("ui-hierarchy").resolve("ui.html")
  assertTrue(hierarchy.exists(), "UI hierarchy does not exist: $hierarchy")

  return screenshotPath
}

fun currentLogFile(driver: Driver): Path {
  return currentLogDir(driver).resolve("idea.log")
}

fun currentLogDir(driver: Driver): Path {
  return Path.of(driver.getSystemProperty(IDEA_LOG_PATH_PROPERTY)).toAbsolutePath().normalize()
}

fun waitForLogText(logFile: Path, text: String) {
  waitFor(
    message = "Log $logFile contains '$text'",
    timeout = 10.seconds,
    errorMessage = { if (logFile.exists()) logFile.readText() else "Log file does not exist: $logFile" },
  ) {
    logFile.exists() && logFile.readText().contains(text)
  }
}

object SplitLogsState {
  var processId: String? = null
  var firstLog: Path? = null
  var firstScreenshot: Path? = null
}

const val IDEA_LOG_PATH_PROPERTY = "idea.log.path"
const val FIRST_LOG_MARKER = "reused split logs first test marker"
const val SECOND_LOG_MARKER = "reused split logs second test marker"
