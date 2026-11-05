// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight

import com.intellij.idea.TestFor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.python.community.testFramework.fixme
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.PythonDocumentationHighlightingServiceImpl
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.TypeEvalContext
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * Code insight work stops at a cancellation checkpoint, so a pending write action does not wait for the whole computation.
 *
 * Each probe runs the work in a read action on a pooled thread, as the highlighting does, with a progress indicator
 * that is cancelled before the work starts. A `ProgressManager.checkCanceled()` call then throws
 * `ProcessCanceledException`, and the probe returns the caller of the first such call. Work without a checkpoint
 * runs to the end, and the probe returns `null`.
 */
@TestFor(classes = [PyTypeChecker::class, PythonDocumentationHighlightingServiceImpl::class])
@Subsystems.CodeInsight
@Layers.Functional
class PyCancellationCheckpointTest : PyCodeInsightTestCase() {

  @Test
  fun `the probe stops at a checkpoint`() {
    val caller = firstCheckpoint { ProgressManager.checkCanceled() }
    assertNotNull(caller)
    assertTrue(caller!!.startsWith(PyCancellationCheckpointTest::class.java.name + "."), caller)
  }

  @Test
  fun `the probe completes an action without a checkpoint`() {
    assertNull(firstCheckpoint { })
  }

  @TestFor(issues = ["PY-92728"])
  @Test
  fun `the match of two wide unions stops at a checkpoint`() {
    val file = configure(wideUnions())
    val context = runReadActionBlocking { TypeEvalContext.codeAnalysis(file.project, file) }
    val (expected, actual) = runReadActionBlocking {
      context.getType(file.findTopLevelAttribute("expected")!!) to context.getType(file.findTopLevelAttribute("actual")!!)
    }
    assertTrue(runReadActionBlocking { PyTypeChecker.match(expected, actual, context) })

    fixme<AssertionError>("PY-92728 PyTypeChecker.match has no checkpoint", "expected: not <null>") {
      assertNotNull(firstCheckpoint { PyTypeChecker.match(expected, actual, context) })
    }
  }

  @TestFor(issues = ["PY-92730"])
  @Test
  fun `the highlighting of a code block in Quick Documentation rethrows the cancellation`() {
    val html = "<pre data-language=\"python\"><code>x = 1</code></pre>"
    val highlighted = runReadActionBlocking {
      PythonDocumentationHighlightingServiceImpl().highlightCodeBlockInHtml(myFixture.project, html)
    }
    assertNotEquals(html, highlighted)

    fixme<AssertionError>("PY-92730 highlightCodeBlockInHtml catches ProcessCanceledException", "expected: not <null>") {
      assertNotNull(firstCheckpoint {
        PythonDocumentationHighlightingServiceImpl().highlightCodeBlockInHtml(myFixture.project, html)
      })
    }
  }

  private fun configure(text: String): PyFile {
    lateinit var file: PyFile
    runInEdtAndWait { file = myFixture.configureByText("a.py", text) as PyFile }
    return file
  }

  /**
   * Runs [action] as described in the class KDoc.
   * Returns the qualified method that calls the first checkpoint, or `null` when the action completes.
   */
  private fun firstCheckpoint(action: () -> Unit): String? {
    return ApplicationManager.getApplication().executeOnPooledThread<String?> {
      val indicator = EmptyProgressIndicator()
      try {
        ProgressManager.getInstance().runProcess({
                                                   indicator.cancel()
                                                   runReadActionBlocking(action)
                                                 }, indicator)
        null
      }
      catch (e: ProcessCanceledException) {
        e.stackTrace.asSequence()
          .map { "${it.className}.${it.methodName}" }
          .dropWhile { it.startsWith(PROGRESS_PACKAGE) }
          .firstOrNull() ?: throw AssertionError("No caller outside $PROGRESS_PACKAGE in the stack", e)
      }
    }.get(1, TimeUnit.MINUTES)
  }

  private companion object {
    const val PROGRESS_PACKAGE = "com.intellij.openapi.progress."
    const val WIDTH = 40

    fun wideUnions(): String = buildString {
      for (i in 1..WIDTH) {
        appendLine("class C$i: ...")
      }
      val union = (1..WIDTH).joinToString(" | ") { "C$it" }
      appendLine("def make_expected() -> $union: ...")
      appendLine("def make_actual() -> $union: ...")
      appendLine("expected = make_expected()")
      appendLine("actual = make_actual()")
    }
  }
}
