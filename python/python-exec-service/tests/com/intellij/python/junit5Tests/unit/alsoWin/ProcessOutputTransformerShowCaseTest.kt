// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.getShell
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.EelSource
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.python.community.execService.*
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.Result
import com.jetbrains.python.getOrThrow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedTest

/**
 * How to use [ProcessOutputTransformer] inheritors.
 */
@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
class ProcessOutputTransformerShowCaseTest {

  @ParameterizedTest
  @EelSource
  fun testProcessOutputTransformer(eelHolder: EelHolder): Unit = timeoutRunBlocking {
    val eel = eelHolder.eel

    data class Record(val name: String, val age: Int)

    val (shell, execArg) = eel.exec.getShell()
    val args = Args(execArg, "echo Alice,25 && echo Bob,48")

    val records = ExecService().execute((BinOnEel(shell.asNioPath())), args) { output ->
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
  @DisabledOnOs(OS.WINDOWS, disabledReason = "echo command creates extra escaping on Windows")
  fun testZeroCodeJsonParserTransformer(eelHolder: EelHolder): Unit = timeoutRunBlocking {
    val eel = eelHolder.eel

    @Serializable
    data class Record(val name: String, val age: Int)

    val testData = listOf(Record("Alice", 25), Record("Bob", 48))

    val (shell, execArg) = eel.exec.getShell()

    val json = Json { ignoreUnknownKeys = true }
    val serialized = json.encodeToString(testData)
    val args = Args(
      execArg,
       if (eel.platform is EelPlatform.Windows) "echo $serialized" else "echo '$serialized'"
    )

    val recordsViaManualDecode = ExecService().execute(
      binary = (BinOnEel(shell.asNioPath())),
      args = args,
      processOutputTransformer = ZeroCodeJsonParserTransformer { jsonString ->
        json.decodeFromString<List<Record>>(jsonString)
      }
    )

    assertEquals(testData, recordsViaManualDecode.getOrThrow())

    val recordsViaGenericTransform = ExecService().execute(
      binary = (BinOnEel(shell.asNioPath())),
      args = args,
      processOutputTransformer = ZeroCodeJsonParserTransformer<List<Record>>(json)
    )

    assertEquals(testData, recordsViaGenericTransform.getOrThrow())
  }
}