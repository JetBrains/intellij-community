// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.ast.impl.PyAstFileImpl
import com.jetbrains.python.ast.impl.PyAstPsiElementFactory
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.validation.PyAstSyntaxAnnotator

/**
 * [PyAstSyntaxAnnotator] on the AST-only PSI, as the light mode of JetBrains Light sees a file. The test masks the
 * stub-based factory and reads the daemon highlights, so a check that needs the stub-based PSI cannot pass here.
 * The file is a light virtual file, so the stub index of the test IDE does not see it. PythonCore still registers the
 * stub factories, and they reject an AST-only tree.
 */
@Subsystems.CodeInsight
@Components.Highlighting
@Layers.Functional
class PyAstSyntaxAnnotatorTest : PyTestCase() {
  override fun setUp() {
    super.setUp()
    ExtensionTestUtil.maskExtensions(PyPsiElementFactory.EP_NAME, listOf(PyAstPsiElementFactory()), testRootDisposable)
  }

  fun testBreakOutsideALoop() {
    assertMessages("break\n", "'break' outside loop")
  }

  fun testDuplicateParameterName() {
    assertMessages("def f(a, a):\n    pass\n", "duplicate parameter name")
  }

  fun testUnterminatedString() {
    assertMessages("s = 'abc\n", "Missing closing quote [']")
  }

  fun testReturnOutsideAFunction() {
    assertMessages("return 1\n", "'return' outside of function")
  }

  fun testStarImportInAFunction() {
    assertMessages("def f():\n    from os import *\n", "'import *' only allowed at module level")
  }

  fun testNameUsedAsGlobalAndParameter() {
    assertMessages("def f(x):\n    global x\n", "Name 'x' is used both as a parameter and as a global")
  }

  fun testSlashParameterFirst() {
    assertMessages("def f(/, a):\n    pass\n", "named parameters must precede bare /")
  }

  fun testNonEmptyReturnInAnAsyncGenerator() {
    assertMessages("async def f():\n    yield 1\n    return 2\n", "non-empty 'return' inside asynchronous generator")
  }

  fun testCorrectCodeHasNoMessages() {
    assertMessages("def f(a, /, b=1, *args, **kwargs):\n    for x in args:\n        break\n    return [a, b]\n")
  }

  /** Configures [text], runs the daemon and checks the error and warning messages against [expected], in order. */
  private fun assertMessages(text: String, vararg expected: String) {
    myFixture.configureFromExistingVirtualFile(LightVirtualFile("a.py", PythonFileType.INSTANCE, text))
    assertInstanceOf(myFixture.file, PyAstFileImpl::class.java)
    val messages = myFixture.doHighlighting(HighlightSeverity.WARNING)
      .filter { it.description != null }
      .sortedBy { it.startOffset }
      .map { it.description }
    assertEquals(expected.toList(), messages)
  }
}
