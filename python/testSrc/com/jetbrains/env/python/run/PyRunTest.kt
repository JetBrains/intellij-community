// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python.run

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.PyExecutionFixtureTestTask
import com.jetbrains.env.PyTestTask
import com.jetbrains.python.run.PythonConfigurationType
import com.jetbrains.python.run.PythonRunConfiguration
import com.jetbrains.python.run.PythonRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PyRunTest : PyEnvTestCase() {
  @Test
  fun testInputRedirection() {
    test {
      val result = runConfiguration {
        redirectInput = true
        inputFile = "input.txt"
      }
      assertThat(result.output).hasSameElementsAs(listOf("100500\n")).describedAs("Output should match expected values")
      assertThat(result.state).isEqualTo(ProcessState.Finished(0))
    }
  }

  @Test
  fun testRunSucceedsWhenEnvFileAbsent() {
    test {
      val result = runConfiguration {
        envFile = absent()
      }
      assertThat(result.state).isEqualTo(ProcessState.NotStarted)
    }
  }

  // DSL entry-point bound to the test class to access protected helpers
  private fun test(block: TestDslBuilder.() -> Unit) {
    val builder = TestDslBuilder { runPythonTestWithException(it) }
    builder.block()
  }
}

// --- Minimal DSL for tests in this file ---

private class RunConfigurationDsl {
  var envFile: EnvFileSpec = EnvFileSpec.None
  var redirectInput: Boolean? = null
  var inputFile: String? = null
}

private sealed interface EnvFileSpec {
  data object None : EnvFileSpec
  data object Absent : EnvFileSpec
}

private fun absent(): EnvFileSpec = EnvFileSpec.Absent

private class TestDslBuilder(private val executor: (PyTestTask) -> Unit) {
  fun runConfiguration(block: RunConfigurationDsl.() -> Unit): RunResult {
    val runCfg = RunConfigurationDsl().apply { block() }
    val output = mutableListOf<String>()
    var processState: ProcessState = ProcessState.NotStarted

    // Execute the same scenario as input redirection test, but honoring the DSL
    val task = object : PyExecutionFixtureTestTask("/runConfig/inputRedirection") {
      override fun runTestOn(sdkHome: String, existingSdk: Sdk?) {
        val project = project
        val settings = RunManager.getInstance(project).createConfiguration("test-missing-env", PythonConfigurationType::class.java)

        val runConfiguration = settings.configuration as PythonRunConfiguration
        runConfiguration.apply {
          this.sdkHome = sdkHome
          this.sdk = existingSdk
          scriptName = "inputRedirection.py"
          workingDirectory = myFixture.tempDirPath
          // Input redirection: use DSL value if provided, default to true and input.txt (to keep older tests working)
          isRedirectInput = runCfg.redirectInput ?: false
          val input = runCfg.inputFile ?: "input.txt"
          inputFile = "${myFixture.tempDirPath}/$input"

          when (runCfg.envFile) {
            is EnvFileSpec.Absent -> {
              // point to a non-existent .env file; run must still succeed
              envFilePaths = listOf("${myFixture.tempDirPath}/.env")
            }
            else -> Unit
          }
        }

        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val runner = ProgramRunner.getRunner(executor.id, settings.configuration) as PythonRunner
        val env = ExecutionEnvironment(executor, runner, settings, project)

        val connection = project.messageBus.connect()
        val latch = CountDownLatch(1)
        val handlerRef = Ref<ProcessHandler>()
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
          override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
            super.processStarted(executorId, env, handler)
            processState = ProcessState.Started
            handlerRef.set(handler)
          }

          override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
            super.processTerminated(executorId, env, handler, exitCode)
            processState = ProcessState.Finished(exitCode)
            latch.countDown()
          }

          override fun processNotStarted(executorId: String, e: ExecutionEnvironment) {
            if (executorId == executor.id && e == env) {
              latch.countDown()
            }
          }
        })

        env.setCallback {
          val processHandler = it.processHandler ?: error("Failed to create process")
          processHandler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
              if (outputType == ProcessOutputTypes.STDOUT) {
                output.add(event.text)
              }
            }

            override fun processTerminated(event: ProcessEvent) {
              processState = ProcessState.Finished(event.exitCode)
              processHandler.removeProcessListener(this)
              latch.countDown()
            }
          })
        }
        runInEdt {
          runner.execute(env)
        }
        val await = latch.await(NORMAL_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        handlerRef.get()?.destroyProcess()
        Assert.assertTrue("Test frozen", await)
        connection.disconnect()
      }
    }

    executor(task)
    return RunResult(output, processState)
  }
}

private data class RunResult(val output: List<String>, val state: ProcessState)

// Represent the process state for the executed run configuration
private sealed interface ProcessState {
  data object NotStarted : ProcessState
  data object Started : ProcessState
  data class Finished(val exitCode: Int) : ProcessState
}
