// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python.testing

import com.intellij.execution.testframework.sm.ServiceMessageUtil
import com.jetbrains.env.EnvTestTagsRequired
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.PyProcessWithConsoleTestTask
import com.jetbrains.env.ut.PyTestTestProcessRunner
import com.jetbrains.python.testing.PyTestConfiguration
import com.jetbrains.python.tools.sdkTools.SdkCreationType
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import org.junit.Assert
import org.junit.Test
import java.net.ServerSocket

@EnvTestTagsRequired(tags = ["pytest"])
class PyTestScriptViaTcpSocketTest : PyEnvTestCase() {

  @Test
  fun testReceivedMessageTypes() {
    val messageReceiver = TcpSocketMessageReceiver()

    runPythonTest(object : PyProcessWithConsoleTestTask<PyTestTestProcessRunner?>("/testRunner/env/pytest", SdkCreationType.EMPTY_SDK) {
      protected override fun createProcessRunner(): PyTestTestProcessRunner {
        return object : PyTestTestProcessRunner("test2.py", 0) {
          override fun configurationCreatedAndWillLaunch(configuration: PyTestConfiguration) {
            configuration.envs = configuration.envs + mapOf(
              "JB_VERBOSE" to "true",
              "JB_DISABLE_BUFFERING" to "true",
              "JB_TEAMCITY_SOCKET_PORT" to messageReceiver.port.toString()
            )
            super.configurationCreatedAndWillLaunch(configuration)
          }
        }
      }

      override fun checkTestResults(
        runner: PyTestTestProcessRunner,
        stdout: String,
        stderr: String,
        all: String,
        exitCode: Int,
      ) {
        Assert.assertEquals(runner.formattedTestTree, 0, runner.allTestsCount)

        messageReceiver.join()

        // with buffering disabled, we want to receive exactly these message types via socket
        Assert.assertEquals(
          messageReceiver.messages.map { it.messageName }.toSet(),
          setOf("enteredTheMatrix", "testCount", "testSuiteStarted", "testStarted", "testFailed", "testFinished", "testSuiteFinished")
        )
      }
    })
  }

  @Test
  fun testKeyboardInterrupt() {
    val messageReceiver = TcpSocketMessageReceiver()

    runPythonTest(object : PyProcessWithConsoleTestTask<PyTestTestProcessRunner?>("/testRunner/env/pytest", SdkCreationType.EMPTY_SDK) {
      protected override fun createProcessRunner(): PyTestTestProcessRunner {
        return object : PyTestTestProcessRunner("test_keyboard_interrupt.py", 0) {
          override fun configurationCreatedAndWillLaunch(configuration: PyTestConfiguration) {
            configuration.envs = configuration.envs + mapOf(
              "JB_VERBOSE" to "true",
              "JB_DISABLE_BUFFERING" to "true",
              "JB_TEAMCITY_SOCKET_PORT" to messageReceiver.port.toString()
            )
            super.configurationCreatedAndWillLaunch(configuration)
          }
        }
      }

      override fun checkTestResults(
        runner: PyTestTestProcessRunner,
        stdout: String,
        stderr: String,
        all: String,
        exitCode: Int,
      ) {
        Assert.assertEquals(runner.formattedTestTree, 0, runner.allTestsCount)

        messageReceiver.join()

        val ignoredMessage = messageReceiver.messages.firstOrNull { it.messageName == "testIgnored" }
        Assert.assertNotNull("No ignored message received", ignoredMessage)
        Assert.assertEquals("Missing stopped information", "true", ignoredMessage!!.attributes["stopped"])
      }
    })
  }
}

private class TcpSocketMessageReceiver {
  private val serverSocket = ServerSocket(0)
  private val receivedMessages = mutableListOf<ServiceMessage>()
  private val socketThread: Thread = Thread {
    serverSocket.use {
      serverSocket.accept().use { socket ->
        socket.inputStream.bufferedReader().use { reader ->
          while (true) {
            val line = reader.readLine() ?: break
            val message = ServiceMessageUtil.parse(line, true)
            Assert.assertNotNull("Failed to parse message: $line", message)
            if (message != null) {
              receivedMessages.add(message)
            }
          }
        }
      }
    }
  }

  val port: Int
    get() = serverSocket.localPort

  val messages: List<ServiceMessage>
    get() = receivedMessages.toList()

  init {
    socketThread.start()
  }

  fun join() {
    socketThread.join()
  }
}