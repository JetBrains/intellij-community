// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring;

import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.refactoring.RefactoringActionHandler;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.refactoring.extractmethod.PyExtractMethodUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class PyExtractMethodTest extends LightMarkedTestCase {

  private void doTest(String newName) {
    final String testName = getTestName(false);
    final String dir = "refactoring/extractmethod/";
    final String beforeName = dir + testName + ".before.py";
    final String afterName = dir + testName + ".after.py";
    final String afterWithTypesName = dir + testName + ".after.withTypes.py";
    final boolean withTypeExists = new File(myFixture.getTestDataPath(), afterWithTypesName).isFile();

    // perform without type annotations
    PyExtractMethodUtil.setAddTypeAnnotations(myFixture.getProject(), false);
    performExtractMethod(newName, beforeName, afterName);

    // perform with type annotations
    FileDocumentManager.getInstance().reloadFromDisk(myFixture.getDocument(myFixture.getFile()), myFixture.getProject());
    PyExtractMethodUtil.setAddTypeAnnotations(myFixture.getProject(), true);
    performExtractMethod(newName, beforeName, withTypeExists ? afterWithTypesName : afterName);
    PyExtractMethodUtil.setAddTypeAnnotations(myFixture.getProject(), false);
  }

  private void performExtractMethod(@NotNull String newName, @NotNull String beforeName, @NotNull String afterName) {
    myFixture.configureByFile(beforeName);
    final RefactoringSupportProvider provider = LanguageRefactoringSupport.getInstance().forLanguage(PythonLanguage.getInstance());
    assertNotNull(provider);
    final RefactoringActionHandler handler = provider.getExtractMethodHandler();
    assertNotNull(handler);
    System.setProperty(PyExtractMethodUtil.NAME, newName);
    try {
      refactorUsingHandler(handler);
    }
    finally {
      System.clearProperty(PyExtractMethodUtil.NAME);
    }
    myFixture.checkResultByFile(afterName);
  }

  private void doFail(String newName, String message) {
    try {
      doTest(newName);
    }
    catch (Exception e) {
      assertEquals(message, e.getMessage());
      return;
    }
    fail("No exception was thrown");
  }

  // PY-34626 
  public void testMethodInnerFunc() {
    doTest("extracted");
  }

  // PY-34626 
  public void testMethodInnerFuncWithOwnParam() {
    doTest("extracted");
  }

  // PY-34626
  public void testMethodInnerFuncWithMethodParam() {
    doTest("extracted");
  }

  // PY-34626
  public void testMethodInnerFuncRecursive() {
    doTest("extracted");
  }

  // PY-34626
  public void testMethodInnerFuncCombined() {
    doTest("extracted");
  }

  // PY-53711
  public void testStartInArgumentListOfMultiLineFunctionCall() {
    doTest("extracted");
  }

  // PY-53711
  public void testStartOnMultiLineFunctionCall() {
    doTest("extracted");
  }

  public void testParameter() {
    doTest("bar");
  }

  public void testBreakAst() {
    doTest("bar");
  }

  public void testExpression() {
    doTest("plus");
  }

  public void testStatement() {
    doTest("foo");
  }

  public void testStatements() {
    doTest("foo");
  }

  public void testStatementReturn() {
    doTest("foo");
  }

  public void testBinaryExpression() {
    doTest("foo");
  }

  public void testWhileOutput() {
    doTest("bar");
  }

  public void testNameCollisionFile() {
    doFail("hello", "The method name clashes with an already existing name");
  }

  public void testNameCollisionSuperClass() {
    doFail("hello", "The method name clashes with an already existing name");
  }

  public void testOutNotEmptyStatements() {
    doTest("sum_squares");
  }

  public void testOutNotEmptyStatements2() {
    doTest("sum_squares");
  }

  public void testFile() {
    doTest("bar");
  }

  public void testMethodContext() {
    doTest("bar");
  }

  public void testMethodIndent() {
    doTest("bar");
  }

  public void testMethodReturn() {
    doTest("bar");
  }

  public void testWrongSelectionIfPart() {
    doFail("bar", "Cannot perform the Extract Method refactoring using the selected elements");
  }

  public void testWrongSelectionFromImportStar() {
    doFail("bar", "Cannot perform refactoring with a star import statement inside a code block");
  }

  public void testPy479() {
    doTest("bar");
  }

  public void testConditionalReturn() {
    doFail("bar", "Cannot perform refactoring when execution flow is interrupted");
  }

  public void testReturnTuple() {
    doTest("bar");
  }

  public void testCommentIncluded() {
    doTest("baz");
  }

  public void testElseBody() {
    doTest("baz");
  }

  public void testClassMethod() {
    doTest("baz");
  }

  public void testStaticMethod() {
    doTest("baz");
  }

  // PY-5123
  public void testMethodInIf() {
    doTest("baz");
  }

  // PY-6081
  public void testLocalVarDefinedBeforeModifiedInside() {
    doTest("bar");
  }

  // PY-6391
  public void testDefinedBeforeAccessedAfter() {
    doTest("bar");
  }

  // PY-5865
  public void testSingleRaise() {
    doTest("bar");
  }

  // PY-4156
  public void testLocalFunction() {
    doTest("bar");
  }

  // PY-6413
  public void testTryFinally() {
    doTest("bar");
  }

  // PY-6414
  public void testTryContext() {
    doTest("bar");
  }

  // PY-6416
  public void testCommentAfterSelection() {
    doTest("bar");
  }

  // PY-6417
  public void testGlobalVarAssignment() {
    doTest("bar");
  }

  // PY-6619
  public void testGlobalToplevelAssignment() {
    doTest("bar");
  }

  // PY-6623
  public void testForLoopContinue() {
    doFail("bar", "Cannot perform refactoring when execution flow is interrupted");
  }

  // PY-6622
  public void testClassWithoutInit() {
    doTest("bar");
  }

  // PY-6625
  public void testNonlocal() {
    doTest("baz");
  }

  // PY-7381
  public void testYield() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      doFail("bar", "Cannot perform refactoring with a 'yield' statement inside a code block");
    });
  }

  // PY-7382
  public void testYield33() {
    doTest("bar");
  }

  // PY-7399
  public void testYieldFrom33() {
    doTest("bar");
  }

  public void testDuplicateSingleLine() {
    doTest("foo");
  }

  public void testDuplicateMultiLine() {
    doTest("foo");
  }

  public void testDuplicateInClass() {
    doTest("foo");
  }

  public void testDuplicateWithRename() {
    doTest("foo");
  }

  public void testDuplicateCheckParam() {
    doTest("foo");
  }

  // PY-7753
  public void testRedundantGlobalInTopLevelFunction() {
    doTest("foo");
  }

  // PY-6620
  public void testProhibitedAtClassLevel() {
    doFail("foo", "Cannot perform refactoring at a class level");
  }

  // PY-9045
  public void testIfConditionExpression() {
    doTest("bar");
  }

  // PY-9045
  public void testIfElseConditionExpression() {
    doTest("bar");
  }

  // PY-9045
  public void testConditionOfConditionalExpression() {
    doTest("bar");
  }

  public void testSimilarBinaryExpressions() {
    doTest("bar");
  }

  public void testAsyncDef() {
    doTest("bar");
  }

  public void testAwaitExpression() {
    doTest("bar");
  }

  public void testCommentsPrecedingSourceStatement() {
    doTest("func");
  }

  // PY-28972
  public void testInterruptedOuterLoop() {
    doFail("foo", "Cannot perform refactoring when execution flow is interrupted");
  }

  // PY-83001
  public void testExtractCompleteBody() {
    doTest("body");
  }

  // PY-35287
  public void testTypedStatements() {
    doTest("greeting");
  }

  public void testPreserveWhitespaceBetweenStatements() {
    doTest("extracted");
  }
}
