// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.python.run

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.PyExecutionFixtureTestTask
import com.jetbrains.python.run.PythonConfigurationType
import com.jetbrains.python.run.PythonRunConfiguration
import com.jetbrains.python.run.PythonRunner
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class PyRunTest : PyEnvTestCase() {
  @Test
  fun testInputRedirection() {
    val task = object : PyExecutionFixtureTestTask("/runConfig/inputRedirection") {
      override fun runTestOn(sdkHome: String, existingSdk: Sdk?) {
        val project = project
        val factory = PythonConfigurationType.getInstance().configurationFactories[0]
        val settings = RunManager.getInstance(project).createRunConfiguration("test", factory)

        val runConfiguration = settings.configuration as PythonRunConfiguration
        runConfiguration.apply {
          this.sdkHome = sdkHome
          scriptName = "inputRedirection.py"
          workingDirectory = myFixture.tempDirPath
          isRedirectInput = true
          inputFile = "${myFixture.tempDirPath}/input.txt"
        }

        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val runner = ProgramRunnerUtil.getRunner(executor.id, settings) as PythonRunner
        val env = ExecutionEnvironment(executor, runner, settings, project)


        var processNotStarted = false
        val connection = project.messageBus.connect()
        val latch = CountDownLatch(1)
        val output = mutableListOf<String>()
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
          override fun processNotStarted(executorId: String, e: ExecutionEnvironment) {
            if (executorId == executor.id && e == env) {
              processNotStarted = true
              latch.countDown()
            }
          }
        })

        runInEdt {
          runner.execute(env) {
            val processHandler = it.processHandler ?: error("Failed to create process")
            processHandler.addProcessListener(object : ProcessAdapter() {
              override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (outputType == ProcessOutputTypes.STDOUT) {
                  output.add(event.text)
                }
              }

              override fun processTerminated(event: ProcessEvent) {
                processHandler.removeProcessListener(this)
                latch.countDown()
              }
            })
          }
        }
        latch.await(myTimeout.toLong(), TimeUnit.MILLISECONDS)
        connection.disconnect()
        if (processNotStarted) {
          Assert.fail("Failed to start the script")
        }
        assertSameElements("Incorrect output", output, listOf("100500\n"))
      }
    }

    runPythonTestWithException(task)
  }
}