// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.systemPython.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.EelApi
import com.intellij.python.community.services.systemPython.SystemPythonProvider
import com.intellij.python.community.services.systemPython.SystemPythonServiceImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.registerExtension
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.job
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

/**
 * An environment can run a startup hook on each start of the interpreter, and such a hook can be expensive.
 * A repeated search must not start an interpreter that did not change, see PY-88315.
 *
 * The test uses a script instead of a real python, because it counts the starts.
 */
@TestApplication
internal class SystemPythonStartCountTest {

  @Test
  fun testUnchangedPythonIsNotStartedAgain(@TempDir dir: Path, @TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(5.minutes) {
    val starts = dir.resolve("starts.txt")
    val python = fakePython(dir, starts)
    ApplicationManager.getApplication().registerExtension(SystemPythonProvider.EP, FixedPythonProvider(python), disposable)
    val sut = SystemPythonServiceImpl(this)

    val firstSearch = sut.findSystemPythons(forceRefresh = true)
    assertThat(firstSearch.map { it.pythonBinary }).contains(python)
    assertThat(starts.startCount()).describedAs("The first search must read the info of $python").isEqualTo(1)

    sut.findSystemPythons(forceRefresh = true)
    assertThat(starts.startCount()).describedAs("$python did not change, so the second search must not start it").isEqualTo(1)

    coroutineContext.job.cancelChildren() // The service updates its cache on the scope of this test
  }

  /**
   * A script that answers the one command the search runs, and records each start in [starts].
   *
   * Windows runs a `.bat`, which `Files.isExecutable` accepts by its extension, and which the exec service starts
   * the same way it starts any other tool.
   */
  private fun fakePython(dir: Path, starts: Path): PythonBinary =
    if (SystemInfoRt.isWindows) {
      dir.resolve("python3.bat").also {
        it.writeText("@echo off\r\necho start >> \"${starts.pathString}\"\r\necho 3.12.0\r\necho True\r\n")
      }
    }
    else {
      dir.resolve("python3").also {
        it.writeText("#!/bin/sh\necho start >> '${starts.pathString}'\necho 3.12.0\necho True\n")
        Files.setPosixFilePermissions(it, PosixFilePermissions.fromString("rwxr-xr-x"))
      }
    }

  /** How many times the script ran. The file is absent while it never ran. */
  private fun Path.startCount(): Int = if (exists()) readLines().count { it.isNotBlank() } else 0

  private class FixedPythonProvider(private val python: PythonBinary) : SystemPythonProvider {
    override suspend fun findSystemPythons(eelApi: EelApi): PyResult<Set<PythonBinary>> = Result.success(setOf(python))
  }
}
