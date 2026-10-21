// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.systemPython.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.services.systemPython.SystemPythonProvider
import com.intellij.python.community.services.systemPython.SystemPythonServiceImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.registerExtension
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.job
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.atomic.AtomicBoolean
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

    val firstSearch = sut.findSystemPythons(localEel, forceRefresh = true)
    assertThat(firstSearch.map { it.pythonBinary }).contains(python)
    assertThat(starts.startCount()).describedAs("The first search must read the info of $python").isEqualTo(1)

    sut.findSystemPythons(localEel, forceRefresh = true)
    assertThat(starts.startCount()).describedAs("$python did not change, so the second search must not start it").isEqualTo(1)

    coroutineContext.job.cancelChildren() // The service updates its cache on the scope of this test
  }

  /**
   * [EelUnavailableException.Conclusive] means that the environment is dead, so the search keeps no record of it.
   * A record would outlive the environment, and each background update would fail on it.
   */
  @Test
  fun testDeadEelIsForgotten(@TempDir dir: Path, @TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(5.minutes) {
    val python = fakePython(dir, dir.resolve("starts.txt"))
    val dead = EelUnavailableException.IntendedExit("The test environment is dead", null)
    ApplicationManager.getApplication().registerExtension(SystemPythonProvider.EP, UnavailableOnceProvider(python, dead), disposable)
    val sut = SystemPythonServiceImpl(this)
    sut.state.userProvidedPythons.add(python.pathString)

    assertThat(sut.findSystemPythons(localEel)).describedAs("The environment is dead, so it has no pythons").isEmpty()
    assertThat(sut.state.userProvidedPythons).describedAs("The settings must not keep a python of a dead environment").doesNotContain(python.pathString)

    coroutineContext.job.cancelChildren() // The service updates its cache on the scope of this test
  }

  /**
   * Eel can wrap its error into a [CancellationException].
   * The search must see the error inside, else the background update stops silently.
   */
  @Test
  fun testDeadEelInCancellationIsForgotten(@TempDir dir: Path, @TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(5.minutes) {
    val python = fakePython(dir, dir.resolve("starts.txt"))
    val dead = CancellationException("The test environment is cancelled", EelUnavailableException.IntendedExit("The test environment is dead", null))
    ApplicationManager.getApplication().registerExtension(SystemPythonProvider.EP, UnavailableOnceProvider(python, dead), disposable)
    val sut = SystemPythonServiceImpl(this)
    sut.state.userProvidedPythons.add(python.pathString)

    assertThat(sut.findSystemPythons(localEel)).describedAs("The environment is dead, so it has no pythons").isEmpty()
    assertThat(sut.state.userProvidedPythons).describedAs("The settings must not keep a python of a dead environment").doesNotContain(python.pathString)

    coroutineContext.job.cancelChildren() // The service updates its cache on the scope of this test
  }

  /**
   * After [EelUnavailableException.CommunicationFailure] a new attempt can succeed.
   * So the search keeps the settings, and the next search tries the environment again.
   */
  @Test
  fun testUnreachableEelIsSearchedAgain(@TempDir dir: Path, @TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(5.minutes) {
    val python = fakePython(dir, dir.resolve("starts.txt"))
    val unreachable = EelUnavailableException.CommunicationFailure("The test environment is unreachable")
    ApplicationManager.getApplication().registerExtension(SystemPythonProvider.EP, UnavailableOnceProvider(python, unreachable), disposable)
    val sut = SystemPythonServiceImpl(this)
    sut.state.userProvidedPythons.add(python.pathString)

    assertThat(sut.findSystemPythons(localEel)).describedAs("The environment is unreachable, so it has no pythons").isEmpty()
    assertThat(sut.state.userProvidedPythons).describedAs("A new attempt can succeed, so the settings must keep $python").contains(python.pathString)
    assertThat(sut.findSystemPythons(localEel).map { it.pythonBinary })
      .describedAs("The cache must not keep the failed search, so the second search must find $python")
      .contains(python)

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

  /** Throws [error] on the first search, so the test does not need a real container. */
  private class UnavailableOnceProvider(
    private val python: PythonBinary,
    private val error: Exception,
  ) : SystemPythonProvider {
    private val unavailable = AtomicBoolean(true)

    override suspend fun findSystemPythons(eelApi: EelApi): PyResult<Set<PythonBinary>> {
      if (unavailable.getAndSet(false)) {
        throw error
      }
      return Result.success(setOf(python))
    }
  }
}
