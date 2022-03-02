// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.pyi.PyiFile
import com.jetbrains.python.pyi.PyiUtil
import com.jetbrains.python.refactoring.inline.PyInlineFunctionHandler
import com.jetbrains.python.refactoring.inline.PyInlineFunctionProcessor

/**
 * @author Aleksei.Kniazev
 */
class PyInlineFunctionTest : PyTestCase() {

  override fun getTestDataPath(): String = super.getTestDataPath() + "/refactoring/inlineFunction"

  private fun doTest(inlineThis: Boolean = true, remove: Boolean = false) {
    val testName = getTestName(true)
    myFixture.copyDirectoryToProject(testName, "")
    myFixture.configureByFile("main.py")
    var element = TargetElementUtil.findTargetElement(myFixture.editor, TargetElementUtil.getInstance().referenceSearchFlags)
    if (element!!.containingFile is PyiFile) element = PyiUtil.getOriginalElement(element as PyElement)
    val reference = TargetElementUtil.findReference(myFixture.editor)
    assertTrue(element is PyFunction)
    PyInlineFunctionProcessor(myFixture.project, myFixture. editor, element as PyFunction, reference, inlineThis, remove).run()
    myFixture.checkResultByFile("$testName/main.after.py")
  }

  private fun doTestError(expectedError: String, isReferenceError: Boolean = false) {
    myFixture.configureByFile("${getTestName(true)}.py")
    val element = TargetElementUtil.findTargetElement(myFixture.editor, TargetElementUtil.getInstance().referenceSearchFlags)
    try {
      if (isReferenceError) {
        val reference = TargetElementUtil.findReference(myFixture.editor)
        PyInlineFunctionProcessor(myFixture.project, myFixture. editor, element as PyFunction, reference, myInlineThisOnly = true, removeDeclaration = false).run()
      }
      else {
        PyInlineFunctionHandler.getInstance().inlineElement(myFixture.project, myFixture.editor, element)
      }
      fail("Expected error: $expectedError, but got none")
    }
    catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
      assertEquals(expectedError, e.message)
    }
  }

  // PY-36803
  fun testAlreadyImported() = doTest()

  // PY-36803
  fun testNameClashWithImport() = doTest()

  fun testSimple() = doTest()
  fun testNameClash() = doTest()
  fun testLocalFunctionUse() = doTest()
  fun testLocalClassUse() = doTest()
  fun testCallAsDefaultValue() = doTest()
  fun testArgumentExtraction() = doTest()
  fun testMultipleReturns() = doTest()
  fun testImporting() = doTest()
  fun testImportAs() = doTest()
  fun testMethodInsideClass() = doTest()
  fun testMethodOutsideClass() = doTest()
  fun testNoReturnsAsExpressionStatement() = doTest()
  fun testNoReturnsAsCallExpression() = doTest()
  fun testInlineAll() = doTest(inlineThis = false)
  fun testRemoving() = doTest(inlineThis = false, remove = true)
  fun testRemoveTypingOverrides() = doTest(inlineThis = false, remove = true)
  fun testDefaultValues() = doTest()
  fun testPositionalOnlyArgs() = doTest()
  fun testKeywordOnlyArgs() = doTest()
  fun testNestedCalls() = doTest(inlineThis = false, remove = true)
  fun testCallFromStaticMethod() = doTest()
  fun testCallFromClassMethod() = doTest()
  fun testComplexQualifier() = doTest()
  fun testRedundantQualifier() = doTest()
  fun testFunctionWithLambda() = doTest()
  fun testRefInDunderAll() = doTest(inlineThis = false, remove = true)
  fun testRemovingDocstring() = doTest()
  fun testRemovingTypeComment() = doTest()
  fun testRemovingDocstringAndTypeComment() = doTest()
  fun testRemovingDocstringOfEmptyFunction() = doTest()
  fun testKeepingComments() = doTest()
  fun testInvocationOnImport() = doTest(inlineThis = false, remove = true)
  fun testImportedLocally() = doTest(inlineThis = false, remove = true)
  fun testSelfUsageDetection() = doTest(inlineThis = false, remove = true)
  fun testIgnoreSolePassStatement() = doTest()
  fun testInlineDocstringOnlyFunction() = doTest()
  fun testTurnDocstringOnlyFunctionIntoPass() = doTest()
  fun testOptimizeImportsAtDeclarationSite() {
    doTest(inlineThis = false, remove = true)
    val testName = getTestName(true)
    myFixture.checkResultByFile("src.py", "$testName/src.after.py",true)
  }
  fun testRemoveFunctionWithStub() {
    doTest(inlineThis = false, remove = true)
    val testName = getTestName(true)
    myFixture.checkResultByFile("main.pyi", "$testName/main.after.pyi",true)
  }
  fun testGenerator() = doTestError("Cannot inline generators")
  fun testAsyncFunction()  {
    runWithLanguageLevel(LanguageLevel.PYTHON37) {
      doTestError("Cannot inline async functions")
    }
  }
  fun testInvocationOnDeclaration() {
    myFixture.configureByFile("${getTestName(true)}.py")
    assertNull("Calling inline function on declaration should not return a reference, but did.", PyInlineFunctionHandler.findReference(myFixture.editor))
  }
  fun testConstructor() = doTestError("Cannot inline constructor calls")
  fun testBuiltin() = doTestError("Cannot inline builtin functions")
  fun testSpecialMethod() = doTestError("Cannot inline special methods")
  fun testDecorator() = doTestError("Cannot inline functions with decorators")
  fun testRecursive() = doTestError("Cannot inline functions that reference themselves")
  fun testStar() = doTestError("Cannot inline functions with * arguments")
  fun testOverrides() = doTestError("Cannot inline methods that override other methods")
  fun testOverridden() = doTestError("Cannot inline overridden methods")
  fun testNested() = doTestError("Cannot inline functions with another function declaration")
  fun testInterruptedFlow() = doTestError("Cannot inline functions that interrupt control flow")
  fun testFunctionFromBinaryStub() {
    runWithAdditionalFileInSkeletonDir("sys.py", "def exit():\n  pass") {
      doTestError("Cannot inline a function from the binary module")
    }
  }
  fun testUsedAsDecorator() = doTestError("The function foo is used as a decorator and cannot be inlined. The function definition will not be removed", isReferenceError = true)
  fun testUsedAsReference() = doTestError("The function foo is used as a reference and cannot be inlined. The function definition will not be removed", isReferenceError = true)
  fun testUsesArgumentUnpacking() = doTestError("The function foo uses argument unpacking and cannot be inlined. The function definition will not be removed", isReferenceError = true)
}