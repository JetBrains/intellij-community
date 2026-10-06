// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.run.DebugAwareConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.concurrency.Promise
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * Tests that Python debug backend resolution happens per launch, not while the platform caches a runner.
 *
 * The resolution is asserted on [findPyDebugBackendRunner], which is the whole body of
 * [PythonDebugProgramRunner.execute] besides the one line that hands the launch to the chosen backend. Driving
 * the runner itself would need either a widened launch hook or `ExecutionManager.startRunProfile` with its tool
 * window and statistics; that the runner has no launch entry point of its own is pinned separately by
 * `PyDebugRunnerAsyncContractTest`.
 */
@TestApplication
@Subsystems.Debugger
@Layers.Functional
internal class PythonDebugProgramRunnerTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @Test
  fun `the backend is resolved at execution time`(@TestDisposable disposable: Disposable) {
    val activeRunnerId = AtomicReference("first")
    val firstRunner = FakeBackendRunner(PyDebuggerBackend.PYDEVD) { activeRunnerId.get() == "first" }
    val secondRunner = FakeBackendRunner(PyDebuggerBackend.PYDEVD) { activeRunnerId.get() == "second" }
    ExtensionTestUtil.maskExtensions(PyDebugBackendRunner.EP_NAME, listOf(firstRunner, secondRunner), disposable)
    val configuration = FakeDebugConfiguration(projectFixture.get())

    assertThat(resolve(configuration)).isSameAs(firstRunner)

    activeRunnerId.set("second")

    assertThat(resolve(configuration))
      .describedAs("the backend must be chosen again on the next launch")
      .isSameAs(secondRunner)
  }

  @Test
  fun `debugpy is preferred when it can run`(@TestDisposable disposable: Disposable) {
    val debugpyRunner = FakeBackendRunner(PyDebuggerBackend.DEBUGPY) { true }
    val pydevdRunner = FakeBackendRunner(PyDebuggerBackend.PYDEVD) { true }
    // pydevd is registered first, so the order of the extension list cannot be what decides.
    ExtensionTestUtil.maskExtensions(PyDebugBackendRunner.EP_NAME, listOf(pydevdRunner, debugpyRunner), disposable)

    assertThat(resolve(FakeDebugConfiguration(projectFixture.get()))).isSameAs(debugpyRunner)
  }

  @Test
  fun `pydevd is the fallback when debugpy cannot run`(@TestDisposable disposable: Disposable) {
    val debugpyRunner = FakeBackendRunner(PyDebuggerBackend.DEBUGPY) { false }
    val pydevdRunner = FakeBackendRunner(PyDebuggerBackend.PYDEVD) { true }
    ExtensionTestUtil.maskExtensions(PyDebugBackendRunner.EP_NAME, listOf(debugpyRunner, pydevdRunner), disposable)

    assertThat(resolve(FakeDebugConfiguration(projectFixture.get()))).isSameAs(pydevdRunner)
  }

  private fun resolve(profile: RunProfile): PyDebugBackendRunner? =
    findPyDebugBackendRunner(DefaultDebugExecutor.EXECUTOR_ID, profile)

  /** Backend whose applicability can be switched between launches. */
  private class FakeBackendRunner(
    override val backend: PyDebuggerBackend,
    private val applicable: () -> Boolean,
  ) : PyDebugBackendRunner {
    override fun isApplicable(executorId: String, profile: RunProfile): Boolean =
      executorId == DefaultDebugExecutor.EXECUTOR_ID && applicable()

    override fun startSession(environment: ExecutionEnvironment): Promise<RunContentDescriptor?> =
      throw UnsupportedOperationException("these tests assert the choice of a backend, never its launch")
  }

  /** Minimal debug-aware run configuration accepted by [PythonDebugProgramRunner]. */
  private class FakeDebugConfiguration(project: Project) :
    RunConfigurationBase<RunConfigurationOptions>(project, null, "Fake"),
    DebugAwareConfiguration {
    override fun canRunUnderDebug(): Boolean = true

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = throw UnsupportedOperationException()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? = null
  }
}
