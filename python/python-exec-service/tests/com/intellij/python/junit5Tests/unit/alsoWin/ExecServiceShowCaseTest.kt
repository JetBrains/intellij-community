// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.getShell
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.EelSource
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.WhatToExec
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.Result
import com.jetbrains.python.getOrThrow
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedTest

/**
 * How to use [ExecService].
 * To exec this test against remote eels, you need `intellij.platform.ijent.testFramework` in classpath (exists on TC)
 */
@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
class ExecServiceShowCaseTest {

  @ParameterizedTest
  @EelSource
  fun testDataTransformer(eelHolder: EelHolder): Unit = timeoutRunBlocking {
    val eel = eelHolder.eel

    data class Record(val name: String, val age: Int)

    val (shell, execArg) = eel.exec.getShell()
    val args = listOf(execArg, "echo Alice,25 && echo Bob,48")

    val records = ExecService().execute(WhatToExec.Binary(shell.asNioPath()), args) { output ->
      when {
        output.exitCode == 123 -> {
          Result.success(emptyList())
        }
        output.exitCode != 0 -> {
          Result.failure(null)
        }
        output.stdout == "SOME_BUSINESS_ERROR" -> {
          Result.failure("My Business Error Description")
        }
        else -> {
          val records = output.stdoutLines.map {
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

    val whatToExec = WhatToExec.Binary.fromRelativeName(eel, binaryName) ?: error("Can't find $binaryName")

    val output = execService.execGetStdout(whatToExec, args.toList()).getOrThrow()
    assertThat("Command doesn't have expected output", output, CoreMatchers.containsString(expectedPhrase))
  }

  @ParameterizedTest
  @EelSource
  fun testRainyDay(eelHolder: EelHolder): Unit = timeoutRunBlocking {
    val eel = eelHolder.eel
    val binary = eel.fs.user.home.asNioPath().resolve("Some_command_that_never_exists_on_any_machine${Math.random()}")
    val arg = "foo"
    val command = WhatToExec.Binary(binary)
    when (val output = ExecService().execGetStdout(command, listOf(arg))) {
      is Result.Success -> fail("Execution of bad command should lead to an error")
      is Result.Failure -> {
        val err = output.error
        assertEquals(command.binary, err.exe.asNioPath(), "Wrong command reported")
        assertEquals("foo", err.args[0], "Wrong args reported")
      }
    }
  }
}