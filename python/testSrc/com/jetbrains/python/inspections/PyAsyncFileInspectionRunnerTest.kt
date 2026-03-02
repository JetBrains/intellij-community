// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.inspections.interpreter.InterpreterFix
import com.jetbrains.python.inspections.interpreter.BusyGuardExecutor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
  private lateinit var expectedFixes: List<InterpreterFix>

  @BeforeEach
  fun setUp() {
    expectedFixes = listOf(TestInterpreterFix("Test Fix"))
  }

  @Test
  fun testInspectionNotBlocked(): Unit = timeoutRunBlocking {
    val barrier = CompletableDeferred<Unit>()
    val runner = PyAsyncFileInspectionRunner(PROGRESS_TITLE) {
      barrier.await()
      InspectionRunnerResult(expectedFixes, true)
    }

    // We expect the first call to return null immediately (as we don't have calculated result yet)
    assertNull(runner.runInspection(module))

    barrier.complete(Unit)
    waitUntilAssertSucceeds(timeout = 5.seconds) {
      val result = runner.runInspection(module)
      assertNotNull(result)
      assertEquals(expectedFixes.size, result!!.size)
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
      val result = runner.runInspection(module)
      assertNotNull(result)
      assertEquals(expectedFixes.size, result!!.size)
    }

    (1..100).map {
      launch {
        val randomDelay = Random.nextInt(2000).milliseconds
        delay(randomDelay)
        runner.runInspection(module)
      }
    }.joinAll()

    val result = runner.runInspection(module)
    assertNotNull(result)
    assertEquals(expectedFixes.size, result!!.size)
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
    assertNull(runner.runInspection(module))

    // Wait for the result of the first run
    waitUntilAssertSucceeds(timeout = 5.seconds) {
      val result = runner.runInspection(module)
      assertNotNull(result)
      assertEquals(expectedFixes.size, result!!.size)
      assertEquals(1, callCount)
    }

    // Should start second run, no result from the cache is returned
    assertNull(runner.runInspection(module))

    // Wait for the result of the second run
    waitUntilAssertSucceeds(timeout = 5.seconds) {
      val result = runner.runInspection(module)
      assertNotNull(result)
      assertEquals(expectedFixes.size, result!!.size)
      assertEquals(2, callCount)
    }
  }
}

private class TestInterpreterFix(val name: String) : InterpreterFix {
  override fun createActionLink(module: Module, project: Project, psiFile: PsiFile, executor: BusyGuardExecutor): com.intellij.ui.components.ActionLink {
    return com.intellij.ui.components.ActionLink(name) {}
  }
}