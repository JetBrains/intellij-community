// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.getShell
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.utils.readWholeText
import com.intellij.platform.eel.provider.utils.sendWholeText
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.EelSource
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.python.community.execService.*
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.Result
import com.jetbrains.python.getOrThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedTest
import org.junitpioneer.jupiter.cartesian.CartesianTest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.minutes

/**
 * How to use [ExecService].
 * To exec this test against remote eels, you need `intellij.platform.ijent.testFramework` in classpath (exists on TC)
 */
@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
class ExecServiceShowCaseTest {
  enum class SimpleApiExecType { IN_SHELL, RELATIVE, FULL_PATH }

  @CartesianTest
  fun testExecSimpleApi(
    @EelSource eelHolder: EelHolder,
    @CartesianTest.Values(booleans = [true, false]) rainyDay: Boolean,
    @CartesianTest.Enum execType: SimpleApiExecType,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val eel = eelHolder.eel
    val sut = ExecService()
    val hello = "hello"

    val (binary, args) = when (eel.platform) {
      is EelPlatform.Windows -> Pair("cmd.exe", arrayOf("/C", "echo $hello\r\nexit\r\n"))
      is EelPlatform.Posix -> Pair("sh", arrayOf("-c", "echo $hello && exit"))
    }

    val r = when (execType) {
      SimpleApiExecType.IN_SHELL -> {
        sut.execGetStdoutInShell(eel, if (rainyDay) "abc123" else "echo $hello")
      }
      SimpleApiExecType.RELATIVE -> {
        sut.execGetStdout(eel, if (rainyDay) "abc123" else binary, args.toList())
      }
      SimpleApiExecType.FULL_PATH -> {
        var fullPath = eel.exec.findExeFilesInPath(binary).firstOrNull()
                       ?: error("no $binary found on ${eel.descriptor.userReadableDescription}")
        if (rainyDay) {
          fullPath = fullPath.resolve("junk")
        }

        sut.execGetStdout(fullPath.asNioPath(), args.toList())
      }
    }

    when (r) {
      is Result.Failure -> {
        assertTrue(rainyDay, "unexpected error ${r.error}")
      }
      is Result.Success -> {
        assertFalse(rainyDay, "unexpected success:${r.result}")
        assertThat("No expected stdout", r.result, CoreMatchers.containsString(hello))
      }
    }
  }


  @ParameterizedTest
  @EelSource
  fun testDataTransformer(eelHolder: EelHolder): Unit = timeoutRunBlocking {
    val eel = eelHolder.eel

    data class Record(val name: String, val age: Int)

    val (shell, execArg) = eel.exec.getShell()
    val args = listOf(execArg, "echo Alice,25 && echo Bob,48")

    val records = ExecService().execute((shell.asNioPath()), args) { output ->
      val stdout = output.stdoutString.trim()
      when {
        output.exitCode == 123 -> {
          Result.success(emptyList())
        }
        output.exitCode != 0 -> {
          Result.failure(null)
        }
        stdout == "SOME_BUSINESS_ERROR" -> {
          Result.failure("My Business Error Description")
        }
        else -> {
          val records = stdout.lines().map { it.trim() }.map {
            val (name, age) = it.split(',')
            Record(name, age.trim().toInt())
          }
          Result.success(records)
        }
      }
    }

    assertEquals(
      listOf(Record("Alice", 25), Record("Bob", 48)),
      records.getOrThrow()
    )
  }

  @ParameterizedTest
  @EelSource
  fun testSunnyDay(eelHolder: EelHolder): Unit = timeoutRunBlocking {
    val eel = eelHolder.eel
    val execService = ExecService()

    val expectedPhrase = "Usage"
    val (binaryName, args) = when (eel.platform) {
      is EelPlatform.Windows -> {
        Pair("ping.exe", arrayOf("/?"))
      }
      is EelPlatform.Posix -> {
        Pair("sh", arrayOf("-c", "echo $expectedPhrase"))
      }
    }

    val whatToExec = eel.exec.findExeFilesInPath(binaryName).firstOrNull() ?: error("Can't find $binaryName")

    val output = execService.execGetStdout(whatToExec.asNioPath(), args.toList()).getOrThrow()
    assertThat("Command doesn't have expected output", output, CoreMatchers.containsString(expectedPhrase))
  }

  @ParameterizedTest
  @EelSource
  fun testInteractive(eelHolder: EelHolder): Unit = timeoutRunBlocking {
    val string = "abc123"
    val shell = eelHolder.eel.exec.getShell().first.asNioPath()
    val output = ExecService().executeAdvanced(shell, {}, processInteractiveHandler = ProcessInteractiveHandler<String> { _, _, process ->
      val stdout = async {
        process.stdout.readWholeText()
      }
      process.stdin.sendWholeText("echo $string\n")
      process.stdin.sendWholeText("exit\n")
      Result.success(stdout.await())
    }).orThrow()
    assertThat("No expected output", output, CoreMatchers.containsString(string))
  }

  @CartesianTest
  fun testSemiInteractive(
    @EelSource eelHolder: EelHolder,
    @CartesianTest.Values(booleans = [true, false]) sunny: Boolean,
  ): Unit = timeoutRunBlocking {
    val messageToUser = "abc123"
    val shell = eelHolder.eel.exec.getShell().first.asNioPath()
    val result = ExecService().executeAdvanced(shell, {}, processInteractiveHandler = processSemiInteractiveHandler<Unit> { channel, exitCode ->
      channel.sendWholeText("exit\n")
      assertEquals(0, exitCode.await().exitCode, "Wrong exit code")
      if (sunny) {
        Result.success(Unit)
      }
      else {
        Result.failure(messageToUser)
      }
    })
    when (result) {
      is Result.Failure -> {
        assertFalse(sunny, "Unexpected failure ${result.error}")
        assertThat("Wrong message to user", result.error.message, CoreMatchers.containsString(messageToUser))
        assertEquals(shell, result.error.exe.asNioPath(), "Wrong exe")
      }
      is Result.Success -> {
        assertTrue(sunny, "Unexpected success")
      }
    }
  }

  @ParameterizedTest
  @EelSource
  fun testRainyDay(eelHolder: EelHolder): Unit = timeoutRunBlocking {
    val eel = eelHolder.eel
    val binary = eel.fs.user.home.asNioPath().resolve("Some_command_that_never_exists_on_any_machine${Math.random()}")
    val arg = "foo"
    when (val output = ExecService().execGetStdout(binary, listOf(arg))) {
      is Result.Success -> fail("Execution of bad command should lead to an error")
      is Result.Failure -> {
        val err = output.error
        assertEquals(binary, err.exe.asNioPath(), "Wrong command reported")
        assertEquals("foo", err.args[0], "Wrong args reported")
      }
    }
  }

  // Process reports something to stdout, we show it as a progress
  @ParameterizedTest
  @EelSource
  fun testProgress(eelHolder: EelHolder): Unit = timeoutRunBlocking(10.minutes) {
    val eel = eelHolder.eel
    val shell = eel.exec.getShell().first.asNioPath()

    val text = "Once there was a captain brave".split(" ").toTypedArray()

    var processStartEvent = false
    var processEndEvent = false

    val progress = CopyOnWriteArrayList<String>()
    val progressCapturer = PyProcessListener { event ->
      when (event) {
        is ProcessEvent.ProcessStarted -> {
          assertEquals(shell, event.binary, "Wrong args for start event")
          processStartEvent = true
        }
        is ProcessEvent.ProcessOutput -> {
          progress.add(event.line)
        }
        is ProcessEvent.ProcessEnded -> {
          processEndEvent = true
        }
      }
    }

    ExecService().executeAdvanced(shell, argsBuilder = {}, processInteractiveHandler = processSemiInteractiveHandler<Unit>(progressCapturer) { stdin, _ ->
      for (string in text) {
        stdin.sendWholeText("echo $string\n")
        delay(500)
      }
      stdin.sendWholeText("exit\n")
      Result.success(Unit)
    }).getOrThrow()
    assertThat("Progress lost", progress, Matchers.containsInRelativeOrder(*text))
    assertTrue(processStartEvent, "no process start event")
    assertTrue(processEndEvent, "no process end event")
  }

  @ParameterizedTest
  @EelSource
  fun testOutputCapture(eelHolder: EelHolder): Unit = timeoutRunBlocking {
    val eel = eelHolder.eel
    val (shell, arg) = eel.exec.getShell()
    var stdoutReported = false
    val text = "abc"
    val listener = PyProcessListener {
      when (it) {
        is ProcessEvent.ProcessOutput -> {
          when (it.stream) {
            ProcessEvent.OutputType.STDOUT -> {
              assertEquals(text, it.line.trim(), "Wrong stdout")
              stdoutReported = true
            }
            ProcessEvent.OutputType.STDERR -> {
              fail("Unexpected stderr ${it.line}")
            }
          }
        }
        is ProcessEvent.ProcessEnded, is ProcessEvent.ProcessStarted -> Unit
      }
    }
    val output = ExecService().execGetStdout(shell.asNioPath(), listOf(arg, "echo $text"), procListener = listener).getOrThrow()
    assertTrue(stdoutReported, "No stdout reported")
    assertEquals(text, output.trim(), "Wrong result")

  }
}