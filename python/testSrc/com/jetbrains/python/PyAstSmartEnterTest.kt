// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.replaceService
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.ast.impl.PyAstPsiElementFactory
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyFile

/**
 * The Complete Statement action on the AST-only PSI of [PyAstPsiElementFactory], which the basic plugin of JetBrains Light uses.
 * The test masks the stub-based factory, so the parser definition builds the AST-only tree.
 */
@Subsystems.Editing
@Layers.Functional
@TestFor(classes = [PySmartEnterProcessor::class])
class PyAstSmartEnterTest : PyTestCase() {
  override fun setUp() {
    super.setUp()
    ApplicationManager.getApplication().replaceService(PyPsiElementFactory::class.java, PyAstPsiElementFactory(), testRootDisposable)
  }

  fun testFunction() = doTest("def foo<caret>", "def foo():\n    <caret>")

  fun testClass() = doTest("class A<caret>", "class A:\n    <caret>")

  fun testIf() = doTest("if x<caret>", "if x:\n    <caret>")

  fun testArgumentList() = doTest("foo(1<caret>", "foo(1)<caret>")

  private fun doTest(before: String, after: String) {
    myFixture.configureByText(PythonFileType.INSTANCE, before)
    assertFalse(myFixture.file is PyFile)
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT)
    myFixture.checkResult(after)
  }
}
