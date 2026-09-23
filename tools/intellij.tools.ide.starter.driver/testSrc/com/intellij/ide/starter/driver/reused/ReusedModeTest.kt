package com.intellij.ide.starter.driver.reused

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import com.intellij.ide.starter.driver.engine.BackgroundRun
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

/**
 * Verifies the basic reused IDE run contract across regular and template test methods.
 *
 * The first test opens an editor and records the [BackgroundRun] and IDE process id. The second test checks
 * that the same run and process are reused, while reused recovery still restores UI state by closing
 * editors opened by the previous test.
 */
@TestMethodOrder(OrderAnnotation::class)
@ReusedRun
class ReusedModeTest {
  companion object {
    @ReusedProject
    private val project: CachedProjectConfiguration = cachedProjectConfiguration(createLightProjectDir())
  }

  @BeforeEach
  fun setUp(driver: Driver) {
    driver.waitForProjectOpen(10.seconds)
  }

  // A parameterized test is a JUnit test template, so this checks that a template method runs in the reused IDE.
  @ParameterizedTest
  @ValueSource(strings = [OPENED_EDITOR_FILE])
  @Order(1)
  fun `first template test opens editor`(fileName: String, bgRun: BackgroundRun, driver: Driver) {
    assertSame(bgRun.driver, driver)

    IdeModeRunRegistry.firstRun = bgRun
    IdeModeRunRegistry.firstProcessId = bgRun.process.id
    assertNotNull(bgRun.process.id)

    var openEditors = emptyList<String>()
    waitFor(
      message = "No editors are open before the first test",
      timeout = 10.seconds,
      errorMessage = { "Open editors: $openEditors" },
    ) {
      openEditors = driver.openEditorFileNames()
      openEditors.isEmpty()
    }

    driver.openFile(fileName, waitForCodeAnalysis = false)
    waitFor("Editor $fileName is opened") {
      fileName in driver.openEditorFileNames()
    }
  }

  @Test
  @Order(2)
  fun `second test reuses the same IDE without open editor`(bgRun: BackgroundRun, driver: Driver) {
    assertSame(checkNotNull(IdeModeRunRegistry.firstRun), bgRun)
    assertEquals(IdeModeRunRegistry.firstProcessId, bgRun.process.id)
    var openEditors = emptyList<String>()
    waitFor(
      message = "All editors are closed after reused IDE recovery",
      timeout = 10.seconds,
      errorMessage = { "Open editors: $openEditors" },
    ) {
      openEditors = driver.openEditorFileNames()
      openEditors.isEmpty()
    }
  }
}

private fun Driver.openEditorFileNames(): List<String> = service<FileEditorManager>(singleProject()).getOpenFiles().map { it.getName() }

private fun createLightProjectDir() = Files.createTempDirectory("reused-ide-run-mode-project").also { projectDir ->
  projectDir.resolve(OPENED_EDITOR_FILE).writeText("Reused IDE run mode test file")
}

private object IdeModeRunRegistry {
  var firstRun: BackgroundRun? = null
  var firstProcessId: String? = null
}

private const val OPENED_EDITOR_FILE = "notes.txt"
