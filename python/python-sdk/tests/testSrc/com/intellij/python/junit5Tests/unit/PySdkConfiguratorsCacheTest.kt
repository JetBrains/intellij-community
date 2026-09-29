// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.Result
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.intellij.serviceContainer.AlreadyDisposedException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger


/**
 * The shared cache over the SDK configurators.
 *
 * What is asserted is the *number of probes*, not their content: answering the question lets every configurator run its
 * tool (`poetry check --lock` and friends), and before this cache each feature that asked paid for its own run.
 */
@TestApplication
internal class PySdkConfiguratorsCacheTest {
  private val projectFixture = projectFixture()
  private val modulePathFixture = tempPathFixture()
  private val module by projectFixture.pyModuleFixture()

  /** Bumped once per probe of [countingConfigurator] — the number this test is about. */
  private val calls = AtomicInteger()

  /** When set, a probe blocks on it, so callers can be made to arrive while one is still running. */
  private var gate: CompletableDeferred<Unit>? = null

  private val countingConfigurator by extensionPointFixture(PyProjectSdkConfigurationExtension.EP_NAME) {
    object : PyProjectSdkConfigurationExtension {
      override val toolId: ToolId = ToolId("counting-test-tool")
      override val potentialDependencyFiles: Set<String> = emptySet()

      override suspend fun checkEnvironmentAndPrepareSdkCreator(pyProject: PyProject, venvs: List<PythonBinary>): CreateInterpreterInfo {
        calls.incrementAndGet()
        gate?.await()
        return CreateInterpreterInfo.ExistingEnv(PythonInfo(LanguageLevel.PYTHON312), "counting") { Result.localizedError("not used") }
      }

      override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null
    }
  }

  @BeforeEach
  fun setUpModule(): Unit = runBlocking {
    // Fixtures initialize on first access, and this one's whole purpose is its registration side effect.
    countingConfigurator

    // A `PyProject` is built from a content root, and the module fixture makes a module without one. Added here and
    // not through the path-based module fixture, because that fixture's tear-down disposes the module a second time,
    // and one test below disposes it itself.
    val contentRoot = withContext(Dispatchers.IO) {
      requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(modulePathFixture.get()))
    }
    edtWriteAction {
      ModuleRootManager.getInstance(module).modifiableModel.apply {
        addContentEntry(contentRoot)
        commit()
      }
    }
  }

  /** The project the cache is asked about. */
  private suspend fun pyProject(): PyProject = requireNotNull(module.asPyProject()) { "$module is not a Python project" }

  @Test
  fun testRepeatedCallsProbeOnce(): Unit = runBlocking {
    val pyProject = pyProject()
    repeat(5) { PyProjectSdkConfigurationExtension.findAllSortedCached(pyProject) }

    assertEquals(1, calls.get(), "the configurators should have been asked once, and the answer reused")
  }

  @Test
  fun testConcurrentCallsShareOneProbe(): Unit = runBlocking {
    val pyProject = pyProject()
    // Held open so every caller arrives while the first probe is still running — the burst this cache exists for, when
    // a project opens and the widget, the notification and the auto-configurator all ask at once.
    val barrier = CompletableDeferred<Unit>()
    gate = barrier

    val results = coroutineScope {
      val waiters = List(8) { async { PyProjectSdkConfigurationExtension.findAllSortedCached(pyProject) } }
      barrier.complete(Unit)
      waiters.awaitAll()
    }

    assertEquals(1, calls.get(), "concurrent callers should share one in-flight probe")
    assertEquals(1, results.map { it.options }.distinct().size, "every caller should get the same answer")
  }

  @Test
  fun testInvalidationForcesAnotherProbe(): Unit = runBlocking {
    val pyProject = pyProject()
    PyProjectSdkConfigurationExtension.findAllSortedCached(pyProject)
    // What a caller does after installing a tool: the previous answer was computed without it.
    PyProjectSdkConfigurationExtension.invalidateCached(pyProject)
    PyProjectSdkConfigurationExtension.findAllSortedCached(pyProject)

    assertEquals(2, calls.get(), "invalidation should force a fresh probe")
  }

  @Test
  fun testUncachedEntryPointAlwaysProbes(): Unit = runBlocking {
    val pyProject = pyProject()
    // The escape hatch for callers whose answer must be true right now (under the SDK-configuration lock, or straight
    // after a tool install) — and for the env tests that change the project on disk and ask again.
    repeat(3) { PyProjectSdkConfigurationExtension.findAllSorted(pyProject) }

    assertEquals(3, calls.get(), "findAllSorted must stay uncached")
  }

  /**
   * A module can be disposed while a caller is on its way here: the automatic configuration walks every project of the
   * project model on a scope of its own, and a project can close under it. Such a module must not be probed — that
   * would run every configurator's tool for something that is gone — and the refusal must be the cancellation the rest
   * of that walk already handles, not the IncorrectOperationException that used to fail it (PY-91964).
   */
  @Test
  fun testDisposedModuleIsNotProbed(): Unit = runBlocking {
    // Resolved while the module is still there, which is what a caller that raced a dispose holds.
    val doomed = pyProject()
    WriteAction.runAndWait<RuntimeException> { ModuleManager.getInstance(module.project).disposeModule(module) }

    assertThrows<AlreadyDisposedException> {
      runBlocking { PyProjectSdkConfigurationExtension.findAllSortedCached(doomed) }
    }
    assertEquals(0, calls.get(), "a disposed module must not be probed")
  }

  @Test
  fun testCachedAnswerCarriesTheProbedVenvs(): Unit = runBlocking {
    val pyProject = pyProject()
    val first = PyProjectSdkConfigurationExtension.findAllSortedCached(pyProject)
    val second = PyProjectSdkConfigurationExtension.findAllSortedCached(pyProject)

    // The venv scan travels with the options, so a caller acting on one does not scan again.
    assertEquals(first.venvs, second.venvs)
    assertEquals(1, calls.get())
  }
}
