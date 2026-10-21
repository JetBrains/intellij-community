package com.intellij.ide.starter.driver.reused

import com.intellij.driver.client.Driver
import com.intellij.driver.client.utility
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

@TestMethodOrder(OrderAnnotation::class)
@ReusedRun(sessionStrategy = RestartBeforeEachRecordingStrategy::class)
class ReusedModeRestartBeforeEachTest {
  companion object {
    @delegate:ReusedProject
    val project: CachedProjectConfiguration by lazy {
      RestartBeforeEachRunRegistry.reset()
      cachedProjectConfiguration(Files.createTempDirectory("reused-restart-before-each-project"))
    }
  }

  @BeforeEach
  fun beforeEach(driver: Driver, bgRun: BackgroundRun) {
    val pathManager = driver.utility<PathManagerRemote>()
    val configDir = Path.of(pathManager.getConfigPath())
    RestartBeforeEachRunRegistry.nonReloadableSettingsFile = configDir.resolve("options/customCssProvider.xml")
    when (RestartBeforeEachRunRegistry.beforeEachCalls++) {
      0 -> {
        RestartBeforeEachRunRegistry.events += "beforeEach:first"
        RestartBeforeEachRunRegistry.firstBeforeEachProcessId = bgRun.process.id
      }
      1 -> {
        RestartBeforeEachRunRegistry.events += "beforeEach:second"
        RestartBeforeEachRunRegistry.secondBeforeEachProcessId = bgRun.process.id
        assertNotEquals(RestartBeforeEachRunRegistry.firstTestProcessId, bgRun.process.id)
        assertFalse(checkNotNull(RestartBeforeEachRunRegistry.nonReloadableSettingsFile).exists())
      }
      else -> error("Unexpected @BeforeEach call")
    }
  }

  @Test
  @Order(1)
  fun `first test changes settings that require restart`(bgRun: BackgroundRun) {
    RestartBeforeEachRunRegistry.events += "first"
    RestartBeforeEachRunRegistry.firstTestProcessId = bgRun.process.id
    assertEquals(RestartBeforeEachRunRegistry.firstBeforeEachProcessId, bgRun.process.id)
    assertTrue(bgRun.process.isAlive)

    val settingsFile = checkNotNull(RestartBeforeEachRunRegistry.nonReloadableSettingsFile)
    settingsFile.parent.createDirectories()
    settingsFile.writeText("<CustomCSSProvider enabled=\"true\" />")
    assertTrue(settingsFile.exists())
  }

  @Test
  @Order(2)
  fun `second test starts after restart required recovery`(bgRun: BackgroundRun) {
    RestartBeforeEachRunRegistry.events += "second"
    RestartBeforeEachRunRegistry.secondTestProcessId = bgRun.process.id

    assertEquals(RestartBeforeEachRunRegistry.secondBeforeEachProcessId, bgRun.process.id)
    assertNotEquals(RestartBeforeEachRunRegistry.firstTestProcessId, bgRun.process.id)
    assertTrue(bgRun.process.isAlive)
    val first = RestartBeforeEachRunRegistry.firstTestProcessId
    val second = RestartBeforeEachRunRegistry.secondTestProcessId
    assertEquals(
      listOf(
        "beforeAllOnIde:$first", "beforeEachOnIde:$first", "beforeEach:first", "first", "afterEachOnIde:$first",
        "beforeAllOnIde:$second", "beforeEachOnIde:$second", "beforeEach:second", "second",
      ),
      RestartBeforeEachRunRegistry.events,
    )
  }
}

private object RestartBeforeEachRunRegistry {
  var beforeEachCalls = 0
  var firstBeforeEachProcessId: String? = null
  var firstTestProcessId: String? = null
  var secondBeforeEachProcessId: String? = null
  var secondTestProcessId: String? = null
  var nonReloadableSettingsFile: Path? = null
  val events = mutableListOf<String>()

  fun reset() {
    beforeEachCalls = 0
    firstBeforeEachProcessId = null
    firstTestProcessId = null
    secondBeforeEachProcessId = null
    secondTestProcessId = null
    nonReloadableSettingsFile = null
    events.clear()
  }
}

/** Records the IDE-side lifecycle calls, so the test can see that the IDE recovery started gets `beforeAll` too. */
internal class RestartBeforeEachRecordingStrategy : ReusedSessionStrategy {
  override fun beforeAllOnIde(context: ExtensionContext, bgRun: BackgroundRun) {
    RestartBeforeEachRunRegistry.events += "beforeAllOnIde:${bgRun.process.id}"
  }

  override fun beforeEachOnIde(context: ExtensionContext, bgRun: BackgroundRun) {
    RestartBeforeEachRunRegistry.events += "beforeEachOnIde:${bgRun.process.id}"
  }

  override fun afterEachOnIde(context: ExtensionContext, bgRun: BackgroundRun) {
    RestartBeforeEachRunRegistry.events += "afterEachOnIde:${bgRun.process.id}"
  }
}
