// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.typeignore

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.idea.TestFor
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Subsystems.Inspections
@Layers.Functional
@TestFor(issues = ["PY-90825"], classes = [PyIgnoreCodeCompletionContributor::class, PyIgnoreCodeTypedHandler::class])
class PyIgnoreCodeCompletionTest : PyCodeInsightTestCase() {

  /** The open bracket is not a type hint, so no Python names such as `PythonFinalizationError` appear. */
  @Test
  fun `offers inspection codes only`() {
    val variants = variants("x = 1  # type: ignore[<caret>")
    assertTrue("unsupported-operator" in variants, variants.toString())
    assertTrue("unresolved-references" in variants, variants.toString())
    assertTrue("PyTypeChecker" in variants, variants.toString())
    assertFalse("PythonFinalizationError" in variants, variants.toString())
  }

  @Test
  fun `completes a granular code`() = checkCompletion(
    before = "print(2 + 'foo')  # type: ignore[unsupported-op<caret>",
    after = "print(2 + 'foo')  # type: ignore[unsupported-operator<caret>",
  )

  @Test
  fun `suppress id completes the kebab alias`() = checkCompletion(
    before = "x = y  # type: ignore[PyUnresolvedRef<caret>",
    after = "x = y  # type: ignore[unresolved-references<caret>",
  )

  @Test
  fun `completes after the pycharm namespace`() = checkCompletion(
    before = "x = y  # type: ignore[pycharm:unresolved-ref<caret>",
    after = "x = y  # type: ignore[pycharm:unresolved-references<caret>",
  )

  @Test
  fun `completes the second code`() = checkCompletion(
    before = "x = y  # type: ignore[attr-defined, unresolved-ref<caret>",
    after = "x = y  # type: ignore[attr-defined, unresolved-references<caret>",
  )

  @Test
  @TestFor(issues = ["PY-90825", "PY-90627"])
  fun `completes in pycharm ignore`() = checkCompletion(
    before = "x = y  # pycharm: ignore[unresolved-ref<caret>",
    after = "x = y  # pycharm: ignore[unresolved-references<caret>",
  )

  @Test
  fun `listed code not offered again`() {
    val variants = variants("x = 1  # type: ignore[bad-return, bad-<caret>")
    assertFalse("bad-return" in variants, variants.toString())
    assertTrue("bad-argument-type" in variants, variants.toString())
  }

  @Test
  fun `listed code with a capitalized namespace not offered again`() {
    val variants = variants("x = 1  # type: ignore[PyCharm:unresolved-references, un<caret>")
    assertFalse("unresolved-references" in variants, variants.toString())
    assertTrue("unsupported-operator" in variants, variants.toString())
  }

  @Test
  fun `listed suppress id hides its alias`() {
    val variants = variants("x = 1  # type: ignore[PyUnresolvedReferences, un<caret>")
    assertFalse("unresolved-references" in variants, variants.toString())
    assertTrue("unsupported-operator" in variants, variants.toString())
  }

  @Test
  fun `completes after a space in the namespace`() = checkCompletion(
    before = "x = y  # type: ignore[pycharm: unresolved-ref<caret>",
    after = "x = y  # type: ignore[pycharm: unresolved-references<caret>",
  )

  @Test
  fun `bracket opens the popup`() = assertEquals(TypedHandlerDelegate.Result.STOP, typed('[', "x = 1  # type: ignore<caret>"))

  @Test
  fun `comma opens the popup`() = assertEquals(TypedHandlerDelegate.Result.STOP, typed(',', "x = 1  # pycharm: ignore[bad-return<caret>"))

  @Test
  fun `bracket in a string does not open the popup`() =
    assertEquals(TypedHandlerDelegate.Result.CONTINUE, typed('[', "print('# type: ignore<caret>')"))

  /** The comment starts with `# noqa`, so it is not an ignore comment. */
  @Test
  fun `bracket after another directive does not open the popup`() =
    assertEquals(TypedHandlerDelegate.Result.CONTINUE, typed('[', "x = 1  # noqa  # type: ignore<caret>"))

  @Test
  fun `bracket in code does not open the popup`() = assertEquals(TypedHandlerDelegate.Result.CONTINUE, typed('[', "x = <caret>"))

  @Test
  fun `no codes in a plain comment`() {
    val variants = variants("x = 1  # see list[<caret>")
    assertFalse("unsupported-operator" in variants, variants.toString())
  }

  private fun variants(text: String): List<String> = runInEdtAndGet {
    myFixture.configureByText(PythonFileType.INSTANCE, text)
    myFixture.completeBasic()
    myFixture.lookupElementStrings.orEmpty()
  }

  private fun typed(char: Char, text: String): TypedHandlerDelegate.Result = runInEdtAndGet {
    myFixture.configureByText(PythonFileType.INSTANCE, text)
    PyIgnoreCodeTypedHandler().checkAutoPopup(char, myFixture.project, myFixture.editor, myFixture.file)
  }

  private fun checkCompletion(before: String, after: String) = runInEdtAndWait {
    myFixture.configureByText(PythonFileType.INSTANCE, before)
    myFixture.completeBasic()
    myFixture.checkResult(after)
  }
}
