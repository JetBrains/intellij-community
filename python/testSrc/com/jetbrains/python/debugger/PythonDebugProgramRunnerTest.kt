// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.run.DebugAwareConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * Tests that Python debug runner backend resolution happens during execution, not while the platform caches a runner.
 */
@TestApplication
internal class PythonDebugProgramRunnerTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @Test
  fun `execute resolves backend runner at execution time`(@TestDisposable disposable: Disposable) {
    val activeRunnerId = AtomicReference("first")
    val firstRunner = FakeBackendRunner("first", PyDebuggerBackend.PYDEVD) { activeRunnerId.get() == "first" }
    val secondRunner = FakeBackendRunner("second", PyDebuggerBackend.PYDEVD) { activeRunnerId.get() == "second" }
    ExtensionTestUtil.maskExtensions(PyDebugBackendRunner.EP_NAME, listOf(firstRunner, secondRunner), disposable)

    val runner = PythonDebugProgramRunner()
    val environment = createEnvironment(runner)

    activeRunnerId.set("second")
    runner.execute(environment)

    assertThat(firstRunner.executionCount).isZero()
    assertThat(secondRunner.executionCount).isEqualTo(1)
    assertThat(secondRunner.executedEnvironment).isSameAs(environment)
  }

  @Test
  fun `execute prefers debugpy backend when it can run`(@TestDisposable disposable: Disposable) {
    val debugpyRunner = FakeBackendRunner("debugpy", PyDebuggerBackend.DEBUGPY) { true }
    val pydevdRunner = FakeBackendRunner("pydevd", PyDebuggerBackend.PYDEVD) { true }
    ExtensionTestUtil.maskExtensions(PyDebugBackendRunner.EP_NAME, listOf(pydevdRunner, debugpyRunner), disposable)

    val runner = PythonDebugProgramRunner()
    runner.execute(createEnvironment(runner))

    assertThat(debugpyRunner.executionCount).isEqualTo(1)
    assertThat(pydevdRunner.executionCount).isZero()
  }

  @Test
  fun `execute falls back to pydevd backend when debugpy cannot run`(@TestDisposable disposable: Disposable) {
    val debugpyRunner = FakeBackendRunner("debugpy", PyDebuggerBackend.DEBUGPY) { false }
    val pydevdRunner = FakeBackendRunner("pydevd", PyDebuggerBackend.PYDEVD) { true }
    ExtensionTestUtil.maskExtensions(PyDebugBackendRunner.EP_NAME, listOf(debugpyRunner, pydevdRunner), disposable)

    val runner = PythonDebugProgramRunner()
    runner.execute(createEnvironment(runner))

    assertThat(debugpyRunner.executionCount).isZero()
    assertThat(pydevdRunner.executionCount).isEqualTo(1)
  }

  private fun createEnvironment(runner: PythonDebugProgramRunner): ExecutionEnvironment {
    val project = projectFixture.get()
    val settings = RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), FakeDebugConfiguration(project))
    return ExecutionEnvironment(DefaultDebugExecutor.getDebugExecutorInstance(), runner, settings, project)
  }

  /** Backend runner whose applicability can be switched after the execution environment is created. */
  private class FakeBackendRunner(
    private val id: String,
    override val backend: PyDebuggerBackend,
    private val canRun: () -> Boolean,
  ) : PyDebugBackendRunner {
    var executionCount: Int = 0
      private set
    var executedEnvironment: ExecutionEnvironment? = null
      private set

    override fun getRunnerId(): String = id

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
      executorId == DefaultDebugExecutor.EXECUTOR_ID && canRun()

    override fun execute(environment: ExecutionEnvironment) {
      executionCount++
      executedEnvironment = environment
    }
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
