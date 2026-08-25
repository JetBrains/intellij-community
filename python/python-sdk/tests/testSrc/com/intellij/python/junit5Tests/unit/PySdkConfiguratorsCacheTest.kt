// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.Result
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

private val EP_NAME: ExtensionPointName<PyProjectSdkConfigurationExtension> =
  ExtensionPointName.create("Pythonid.projectSdkConfigurationExtension")

/**
 * The shared cache over the SDK configurators.
 *
 * What is asserted is the *number of probes*, not their content: answering the question lets every configurator run its
 * tool (`poetry check --lock` and friends), and before this cache each feature that asked paid for its own run.
 */
@TestApplication
internal class PySdkConfiguratorsCacheTest {
  private val projectFixture = projectFixture()
  private val module by projectFixture.pyModuleFixture()

  /** Bumped once per probe of [countingConfigurator] — the number this test is about. */
  private val calls = AtomicInteger()

  /** When set, a probe blocks on it, so callers can be made to arrive while one is still running. */
  private var gate: CompletableDeferred<Unit>? = null

  private val countingConfigurator by extensionPointFixture(EP_NAME) {
    object : PyProjectSdkConfigurationExtension {
      override val toolId: ToolId = ToolId("counting-test-tool")
      override val potentialDependencyFiles: Set<String> = emptySet()

      override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo {
        calls.incrementAndGet()
        gate?.await()
        return CreateSdkInfo.ExistingEnv(PythonInfo(LanguageLevel.PYTHON312), "counting") { Result.localizedError("not used") }
      }

      override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null
    }
  }

  @BeforeEach
  fun registerConfigurator() {
    // Fixtures initialize on first access, and this one's whole purpose is its registration side effect.
    countingConfigurator
  }

  @Test
  fun testRepeatedCallsProbeOnce(): Unit = runBlocking {
    repeat(5) { PyProjectSdkConfigurationExtension.findAllSortedForModuleCached(module) }

    assertEquals(1, calls.get(), "the configurators should have been asked once, and the answer reused")
  }

  @Test
  fun testConcurrentCallsShareOneProbe(): Unit = runBlocking {
    // Held open so every caller arrives while the first probe is still running — the burst this cache exists for, when
    // a project opens and the widget, the notification and the auto-configurator all ask at once.
    val barrier = CompletableDeferred<Unit>()
    gate = barrier

    val results = coroutineScope {
      val waiters = List(8) { async { PyProjectSdkConfigurationExtension.findAllSortedForModuleCached(module) } }
      barrier.complete(Unit)
      waiters.awaitAll()
    }

    assertEquals(1, calls.get(), "concurrent callers should share one in-flight probe")
    assertEquals(1, results.map { it.options }.distinct().size, "every caller should get the same answer")
  }

  @Test
  fun testInvalidationForcesAnotherProbe(): Unit = runBlocking {
    PyProjectSdkConfigurationExtension.findAllSortedForModuleCached(module)
    // What a caller does after installing a tool: the previous answer was computed without it.
    PyProjectSdkConfigurationExtension.invalidateCachedForModule(module)
    PyProjectSdkConfigurationExtension.findAllSortedForModuleCached(module)

    assertEquals(2, calls.get(), "invalidation should force a fresh probe")
  }

  @Test
  fun testUncachedEntryPointAlwaysProbes(): Unit = runBlocking {
    // The escape hatch for callers whose answer must be true right now (under the SDK-configuration lock, or straight
    // after a tool install) — and for the env tests that change the project on disk and ask again.
    repeat(3) { PyProjectSdkConfigurationExtension.findAllSortedForModule(module) }

    assertEquals(3, calls.get(), "findAllSortedForModule must stay uncached")
  }

  @Test
  fun testCachedAnswerCarriesTheProbedVenvs(): Unit = runBlocking {
    val first = PyProjectSdkConfigurationExtension.findAllSortedForModuleCached(module)
    val second = PyProjectSdkConfigurationExtension.findAllSortedForModuleCached(module)

    // The venv scan travels with the options, so a caller acting on one does not scan again.
    assertEquals(first.venvsInModule, second.venvsInModule)
    assertEquals(1, calls.get())
  }
}
