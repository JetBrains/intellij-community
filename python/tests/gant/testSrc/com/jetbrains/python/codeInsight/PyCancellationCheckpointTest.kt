// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight

import com.intellij.idea.TestFor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.progress.impl.ProgressManagerImpl
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.python.community.testFramework.fixme
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.PythonDocumentationHighlightingServiceImpl
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyClassImpl
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeVarType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.junit.jupiter.api.Assertions.assertEquals
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
 * runs to the end, and the probe returns `null`. For work that starts with a checkpoint of the platform, such as
 * a PSI access, the probe cancels the indicator only at the first checkpoint of the method under test.
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

    assertEquals(PY_TYPE_CHECKER_MATCH, firstCheckpoint { PyTypeChecker.match(expected, actual, context) })
  }

  @TestFor(issues = ["PY-92728"])
  @Test
  fun `the substitution of a generic type stops at a checkpoint`() {
    val file = configure("""
      def f[T](x: T) -> tuple[T, T]: ...
    """.trimIndent())
    val context = runReadActionBlocking { TypeEvalContext.codeAnalysis(file.project, file) }
    val (returnType, substitutions) = runReadActionBlocking {
      val returnType = context.getReturnType(file.findTopLevelFunction("f")!!) as PyTupleType
      val typeVar = returnType.elementTypes.first() as PyTypeVarType
      returnType to PyTypeChecker.GenericSubstitutions(mapOf(typeVar to PyBuiltinCache.getInstance(file).intType))
    }
    assertEquals("tuple[int, int]", runReadActionBlocking {
      PythonDocumentationProvider.getTypeName(PyTypeChecker.substitute(returnType, substitutions, context), context)
    })

    assertEquals(PY_TYPE_CHECKER_SUBSTITUTE, firstCheckpoint { PyTypeChecker.substitute(returnType, substitutions, context) })
  }

  @TestFor(issues = ["PY-92728"], classes = [PyClassImpl::class])
  @Test
  fun `the MRO merge stops at a checkpoint`() {
    val file = configure("""
      class B3: ...
      class B2(B3): ...
      class B1(B2): ...
      class Top(B1, B2, B3): ...
    """.trimIndent())
    val top = runReadActionBlocking { file.findTopLevelClass("Top")!! }

    val caller = firstCheckpointCalledBy(PY_CLASS_MRO_MERGE) {
      top.getAncestorTypes(TypeEvalContext.codeAnalysis(file.project, file))
    }

    assertEquals(PY_CLASS_MRO_MERGE, caller)
    assertEquals(listOf("B1", "B2", "B3", "object"),
                 runReadActionBlocking { top.getAncestorTypes(TypeEvalContext.codeAnalysis(file.project, file)).map { it?.name } })
  }

  @TestFor(issues = ["PY-92730"])
  @Test
  fun `the highlighting of a code block in Quick Documentation rethrows the cancellation`() {
    val html = "<pre data-language=\"python\"><code>x = 1</code></pre>"
    val highlighted = runReadActionBlocking {
      PythonDocumentationHighlightingServiceImpl().highlightCodeBlockInHtml(myFixture.project, html)
    }
    assertNotEquals(html, highlighted)

    assertNotNull(firstCheckpoint {
      PythonDocumentationHighlightingServiceImpl().highlightCodeBlockInHtml(myFixture.project, html)
    })
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
        callerOfCheckpoint(e)
      }
    }.get(1, TimeUnit.MINUTES)
  }

  /**
   * Runs [action] in a read action on a pooled thread, with a progress indicator that a check-canceled hook cancels
   * at the first checkpoint that [caller] calls. That checkpoint throws `ProcessCanceledException`.
   * Returns the qualified method that calls the checkpoint that throws, or `null` when the action completes.
   */
  private fun firstCheckpointCalledBy(caller: String, action: () -> Unit): String? {
    return ApplicationManager.getApplication().executeOnPooledThread<String?> {
      val worker = Thread.currentThread()
      // Unlike EmptyProgressIndicator, it runs the hooks in each checkCanceled and throws when a hook cancels it
      val indicator = ProgressIndicatorBase()
      val hook = CoreProgressManager.CheckCanceledHook { _ ->
        val cancel = Thread.currentThread() === worker && !indicator.isCanceled && currentCheckpointCaller() == caller
        if (cancel) indicator.cancel()
        cancel
      }
      try {
        (ProgressManager.getInstance() as ProgressManagerImpl).runWithHook(hook) {
          ProgressManager.getInstance().runProcess({ runReadActionBlocking(action) }, indicator)
        }
        null
      }
      catch (e: ProcessCanceledException) {
        callerOfCheckpoint(e)
      }
    }.get(1, TimeUnit.MINUTES)
  }

  private fun currentCheckpointCaller(): String? = StackWalker.getInstance().walk { frames ->
    frames.map { "${it.className}.${it.methodName}" }
      .dropWhile { it.startsWith(PROGRESS_PACKAGE) || it.startsWith(PyCancellationCheckpointTest::class.java.name) }
      .findFirst()
      .orElse(null)
  }

  private fun callerOfCheckpoint(e: ProcessCanceledException): String =
    e.stackTrace.asSequence()
      .map { "${it.className}.${it.methodName}" }
      .dropWhile { it.startsWith(PROGRESS_PACKAGE) }
      .firstOrNull() ?: throw AssertionError("No caller outside $PROGRESS_PACKAGE in the stack", e)

  private companion object {
    const val PROGRESS_PACKAGE = "com.intellij.openapi.progress."
    const val WIDTH = 40
    val PY_TYPE_CHECKER_MATCH = PyTypeChecker::class.java.name + ".match"
    val PY_TYPE_CHECKER_SUBSTITUTE = PyTypeChecker::class.java.name + ".substitute"
    val PY_CLASS_MRO_MERGE = PyClassImpl::class.java.name + ".mroMerge"

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
