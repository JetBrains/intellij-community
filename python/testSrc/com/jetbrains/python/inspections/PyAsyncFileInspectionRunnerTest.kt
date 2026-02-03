// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.inspections.interpreter.InterpreterSettingsQuickFix
import com.jetbrains.python.psi.PyFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val PROGRESS_TITLE = "Test Progress"

@TestApplication
class PyAsyncFileInspectionRunnerTest {

  private val projectFixture = projectFixture()
  private val module by projectFixture.moduleFixture()
  private lateinit var pyFile: PyFile
  private lateinit var expectedFixes: List<LocalQuickFix>

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    pyFile = backgroundWriteAction {
      PsiFileFactory.getInstance(projectFixture.get()).createFileFromText("test.py", PythonLanguage.getInstance(), "print(1)") as PyFile
    }
    expectedFixes = listOf(InterpreterSettingsQuickFix(module))
  }

  @Test
  fun testInspectionNotBlocked(): Unit = timeoutRunBlocking {
    val barrier = CompletableDeferred<Unit>()
    val runner = PyAsyncFileInspectionRunner(PROGRESS_TITLE) {
      barrier.await()
      InspectionRunnerResult(expectedFixes, true)
    }

    // We expect the first call to return null immediately (as we don't have calculated result yet)
    assertNull(runner.runInspection(pyFile, module))

    barrier.complete(Unit)
    waitUntilAssertSucceeds(timeout = 5.seconds) {
      assertIterableEquals(expectedFixes, runner.runInspection(pyFile, module))
    }
  }

  @Test
  fun testInspectionResultCached(): Unit = timeoutRunBlocking(timeout = 30.seconds) {
    var callCount = 0
    val runner = PyAsyncFileInspectionRunner(PROGRESS_TITLE, cacheTtl = 10.seconds) {
      callCount++
      InspectionRunnerResult(expectedFixes, true)
    }

    waitUntilAssertSucceeds(timeout = 5.seconds) {
      assertIterableEquals(expectedFixes, runner.runInspection(pyFile, module))
    }

    (1..100).map {
      launch {
        val randomDelay = Random.nextInt(2000).milliseconds
        delay(randomDelay)
        runner.runInspection(pyFile, module)
      }
    }.joinAll()

    assertIterableEquals(expectedFixes, runner.runInspection(pyFile, module))
    assertEquals(1, callCount)
  }

  @Test
  fun testInspectionResultNotCached(): Unit = timeoutRunBlocking {
    var callCount = 0
    val runner = PyAsyncFileInspectionRunner(PROGRESS_TITLE) {
      delay(500.milliseconds)
      callCount++
      InspectionRunnerResult(expectedFixes, false)
    }

    // First run, no result yet
    assertNull(runner.runInspection(pyFile, module))

    // Wait for the result of the first run
    waitUntilAssertSucceeds(timeout = 5.seconds) {
      assertIterableEquals(expectedFixes, runner.runInspection(pyFile, module))
      assertEquals(1, callCount)
    }

    // Should start second run, no result from the cache is returned
    assertNull(runner.runInspection(pyFile, module))

    // Wait for the result of the second run
    waitUntilAssertSucceeds(timeout = 5.seconds) {
      assertIterableEquals(expectedFixes, runner.runInspection(pyFile, module))
      assertEquals(2, callCount)
    }
  }
}