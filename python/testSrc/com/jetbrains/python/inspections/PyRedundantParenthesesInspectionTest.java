// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;


public class PyRedundantParenthesesInspectionTest extends PyInspectionTestCase {
  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyRedundantParenthesesInspection.class;
  }

  @Override
  protected boolean isLowerCaseTestFile() {
    return false;
  }

  public void testBooleanMultiline() {
    doTest();
  }

  public void testFormatting() {
    doTest();
  }

  public void testIfElif() {
    doTest();
  }

  public void testIfMultiline() {
    doTest();
  }

  public void testStringMultiline() {
    doTest();
  }

  public void testTryExcept() {
    doTest();
  }

  public void testTryExceptNegate() {
    doTest();
  }

  public void testWhile() {
    doTest();
  }

  public void testYieldFrom() {       //PY-7410
    doTest();
  }

  public void testYieldExpression() {       //PY-10420
    doTest();
  }

  public void testBinaryInBinary() {       //PY-10420
    doTest();
  }

  // PY-31795
  public void testSyntaxErrorInside() {
    doTest();
  }

  // PY-31795
  public void testSingleLeftParenthesis() {
    doTest();
  }

  public void testParenthesizedTupleInReturn() {
    doTest();
  }

  public void testParenthesizedTupleInYield() {
    doTest();
  }

  // PY-33266
  public void testNestedParentheses() {
    doTest();
  }

  // PY-21530
  public void testReferenceExpression() {
    doTest();
  }

  // PY-20324
  public void testParenthesizedTupleWithUnpackingInReturnBefore38() {
    runWithLanguageLevel(LanguageLevel.PYTHON37, this::doTest);
  }

  // PY-35961
  public void testParenthesizedTupleWithUnpackingInReturn() {
    doTest();
  }

  // PY-20324
  public void testParenthesizedTupleWithUnpackingInYieldBefore38() {
    runWithLanguageLevel(LanguageLevel.PYTHON37, this::doTest);
  }

  // PY-35961
  public void testParenthesizedTupleWithUnpackingInYield() {
    doTest();
  }

  // PY-35961
  public void testParenthesizedTupleWithUnpackingInYieldFrom() {
    doTest();
  }

  // PY-34262
  public void testReturnOneElementTuple() {
    doTestByText("def foo():\n" +
                 "  return (1, )");
  }

  // PY-45143
  public void testNoInspectionWithYieldFrom() {
    doTest();
  }
}
