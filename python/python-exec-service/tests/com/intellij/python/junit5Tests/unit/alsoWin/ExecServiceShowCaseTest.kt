// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.where
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.WhatToExec
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.Result
import com.jetbrains.python.getOrThrow
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * How to use [ExecService]
 */
@TestApplication
class ExecServiceShowCaseTest {

  private val eel = localEel // TODO: Check other eels

  @Test
  fun testDataTransformer(): Unit = timeoutRunBlocking {
    data class Record(val name: String, val age: Int)

    val echoCommand = WhatToExec.Command(eel, "echo")
    val args = listOf("Alice,25\nBob,48\n")

    val records = ExecService().execute(echoCommand, args) { output ->
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
            Record(name, age.toInt())
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
  @ValueSource(booleans = [true, false])
  fun testSunnyDay(useLocalPath: Boolean): Unit = timeoutRunBlocking {
    val execService = ExecService()

    val expectedPhrase = "Usage"
    val (command, args) = when (eel.platform) {
      is EelPlatform.Windows -> Pair("ping.exe", arrayOf("/?"))
      is EelPlatform.Posix -> {
        Pair("sh", arrayOf("-c", "echo $expectedPhrase"))
      }
    }

    val whatToExec = if (useLocalPath) {
      val fullPath = (eel.exec.where(command) ?: error("no $command found on $eel")).asNioPath()
      WhatToExec.Binary(fullPath)
    }
    else {
      WhatToExec.Command(eel, command)
    }

    val output = execService.execGetStdout(whatToExec, args.toList()).getOrThrow()
    assertThat("Command doesn't have expected output", output, CoreMatchers.containsString(expectedPhrase))
  }

  @Test
  fun testRainyDay(): Unit = timeoutRunBlocking {
    val arg = "foo"
    val command = WhatToExec.Command(eel, "Some_command_that_never_exists_on_any_machine${Math.random()}")
    when (val output = ExecService().execGetStdout(command, listOf(arg))) {
      is Result.Success -> fail("Execution of bad command should lead to an error")
      is Result.Failure -> {
        val err = output.error
        val failure = err.execFailure
        assertEquals(command.command, failure.command, "Wrong command reported")
        assertEquals("foo", failure.args[0], "Wrong args reported")
      }
    }
  }
}