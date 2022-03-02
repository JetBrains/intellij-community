// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring;

import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.constant.PyIntroduceConstantHandler;


@TestDataPath("$CONTENT_ROOT/../testData/refactoring/introduceConstant/")
public class PyIntroduceConstantTest extends PyIntroduceTestCase {
  public void testPy1840() {
    doTest();
  }

  public void testPy1840EntireLine() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doTest);
  }

  public void testInsertAfterImport() {  // PY-2149
    doTest();
  }

  public void testInsertAfterDocstring() { // PY-3657
    doTest();
  }

  public void testSuggestUniqueNames() {  // PY-4409
    doTestSuggestions(PyExpression.class, "S1");
  }

  public void testSuggestUniqueNamesGlobalScope() {  // PY-4409
    doTestSuggestions(PyExpression.class, "S1");
  }

  public void testPy4414() {
    doTestInplace(null);
  }

  // PY-13484
  public void testFromParameterDefaultValue() {
    doTest();
  }

  // PY-23500
  public void testInsertAfterGlobalVariableOnWhichDepends() {
    doTest();
  }

  // PY-23500
  public void testInsertAfterAllGlobalVariablesOnWhichDepends() {
    doTest();
  }

  // PY-23500
  public void testInsertAfterWithStatementOnWhichDependsRefactoringError() {
    doTestThrowsRefactoringErrorHintException();
  }

  // PY-23500
  public void testInsertAfterLocalVariableOnWhichDependsRefactoringError() {
    doTestThrowsRefactoringErrorHintException();
  }

  // PY-23500
  public void testInsertAfterFunctionParameterOnWhichDependsRefactoringError() {
    doTestThrowsRefactoringErrorHintException();
  }

  // PY-23500
  public void testInsertAfterForIteratorOnWhichDependsRefactoringError() {
    doTestThrowsRefactoringErrorHintException();
  }

  // PY-23500
  public void testInsertAfterLocalVariableInForLoopOnWhichDependsRefactoringError() {
    doTestThrowsRefactoringErrorHintException();
  }

  // PY-23500
  public void testInsertAfterIfElse() {
    doTest();
  }

  // PY-23500
  public void testFromImportTopLevel() {
    doTest();
  }

  // PY-23500
  public void testFromImportInFunctionRefactoringError() {
    doTestThrowsRefactoringErrorHintException();
  }

  // PY-23500
  public void testExpressionWithFunctionCall() {
    doTest();
  }

  // PY-23500
  public void testExpressionWithParameterRefactoringError() {
    doTestThrowsRefactoringErrorHintException();
  }

  // PY-23500
  public void testSubexpressionWithParameterRefactoringError() {
    doTestThrowsRefactoringErrorHintException();
  }

  // PY-23500
  public void testSubexpressionWithGlobal() {
    doTest();
  }

  // PY-23500
  public void testSubexpressionNotFullWordRefactoringError() {
    doTestThrowsRefactoringErrorHintException();
  }

  // PY-33843
  public void testStringLiteralArgumentInComprehension() {
    doTest();
  }

  private void doTestThrowsRefactoringErrorHintException() {
    assertThrows(CommonRefactoringUtil.RefactoringErrorHintException.class, () -> doTest());
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/introduceConstant";
  }

  @Override
  protected IntroduceHandler createHandler() {
    return new PyIntroduceConstantHandler();
  }
}
