package com.intellij.ide.starter.driver.reused

import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import com.intellij.ide.starter.ide.IDEStartConfig
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.util.system.OS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.writeText

class ReusedProjectConfigurationTest {
  @Test
  fun `cached project defaults are used when no local setup is declared`() {
    HookTracker.reset()

    val configuration = findReusedProjectConfiguration(PlainCompanionReusedIdeTest::class.java)
    val context = createDummyTestContext("plain", configuration.testCase)

    configuration.setup(context)

    assertEquals(1, HookTracker.baseSetupCalls)
    assertEquals(0, HookTracker.localSetupCalls)
  }

  @Test
  fun `local hooks extend cached project configuration`() {
    HookTracker.reset()

    val configuration = findReusedProjectConfiguration(CustomCompanionReusedIdeTest::class.java)
    val context = createDummyTestContext("custom", configuration.testCase)

    configuration.setup(context)

    assertEquals(1, HookTracker.baseSetupCalls)
    assertEquals(1, HookTracker.localSetupCalls)
    assertEquals(listOf("baseSetup", "localSetup"), HookTracker.events)
  }

  @Test
  fun `private companion project field is accepted`() {
    HookTracker.reset()

    val configuration = findReusedProjectConfiguration(PrivateCompanionReusedIdeTest::class.java)
    val context = createDummyTestContext("private", configuration.testCase)

    configuration.setup(context)

    assertEquals(1, HookTracker.baseSetupCalls)
    assertEquals(1, HookTracker.localSetupCalls)
    assertEquals(listOf("baseSetup", "privateSetup"), HookTracker.events)
  }
}

@ReusedRun
private abstract class CompanionReusedIdeTestBase {
  companion object {
    @ReusedProject
    val project: CachedProjectConfiguration =
      CachedProjectConfiguration(
        testCase = TestCase(
          ideInfo = reusedDriverTestIdeInfo,
          projectInfo = LocalProjectInfo(
            projectDir = Files.createTempDirectory("companion-project"),
            isReusable = true,
          ),
        ).useRelease(),
        setup = {
          applyVMOptionsPatch { addSystemProperty("idea.trust.all.projects", true) }
          HookTracker.baseSetupCalls += 1
          HookTracker.events += "baseSetup"
          this
        },
      )
  }
}

private class PlainCompanionReusedIdeTest : CompanionReusedIdeTestBase()

private class CustomCompanionReusedIdeTest : CompanionReusedIdeTestBase() {
  companion object {
    @delegate:ReusedProject
    val project: CachedProjectConfiguration by lazy {
      val base = CompanionReusedIdeTestBase.project
      CachedProjectConfiguration(base.testCase) {
        base.setup(this).apply {
          HookTracker.localSetupCalls += 1
          HookTracker.events += "localSetup"
        }
      }
    }
  }
}

private class PrivateCompanionReusedIdeTest : CompanionReusedIdeTestBase() {
  @Suppress("unused")
  private companion object {
    @delegate:ReusedProject
    private val project: CachedProjectConfiguration by lazy {
      val base = CompanionReusedIdeTestBase.project
      CachedProjectConfiguration(base.testCase) {
        base.setup(this).apply {
          HookTracker.localSetupCalls += 1
          HookTracker.events += "privateSetup"
        }
      }
    }
  }
}

private object HookTracker {
  var baseSetupCalls = 0
  var localSetupCalls = 0
  val events = mutableListOf<String>()

  fun reset() {
    baseSetupCalls = 0
    localSetupCalls = 0
    events.clear()
  }
}

private fun createDummyTestContext(testName: String, testCase: TestCase<*>): IDETestContext {
  val testHome = Files.createTempDirectory("companion-context")
  return IDETestContext(
    paths = IDEDataPaths(testHome, null),
    ide = DummyInstalledIde(Files.createTempDirectory("companion-ide")),
    testCase = testCase,
    testName = testName,
    _resolvedProjectHome = testHome,
    publishers = emptyList(),
  )
}

private class DummyInstalledIde(private val home: Path) : InstalledIde {
  private val vmOptionsFile = home.resolve("idea.vmoptions").createFile().apply { writeText("") }

  override val vmOptions: VMOptions = VMOptions.readIdeVMOptions(this, vmOptionsFile)
  override val build: String = "0.0"
  override val os: OS = OS.macOS
  override val productCode: String = "IU"
  override val isFromSources: Boolean = false
  override val installationPath: Path = home

  override fun startConfig(vmOptions: VMOptions, logsDir: Path): IDEStartConfig {
    return object : IDEStartConfig {
      override val workDir: Path = logsDir
      override val commandLine: List<String> = emptyList()
    }
  }

  override suspend fun resolveAndDownloadTheSameJDK(): Path = home
}
