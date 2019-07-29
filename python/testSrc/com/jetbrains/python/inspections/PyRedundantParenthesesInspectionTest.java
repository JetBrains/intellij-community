/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
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
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doTest);
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
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-35961
  public void testParenthesizedTupleWithUnpackingInReturn() {
    runWithLanguageLevel(LanguageLevel.PYTHON38, this::doTest);
  }

  // PY-20324
  public void testParenthesizedTupleWithUnpackingInYieldBefore38() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  // PY-35961
  public void testParenthesizedTupleWithUnpackingInYield() {
    runWithLanguageLevel(LanguageLevel.PYTHON38, this::doTest);
  }

  // PY-35961
  public void testParenthesizedTupleWithUnpackingInYieldFrom() {
    runWithLanguageLevel(LanguageLevel.PYTHON38, this::doTest);
  }
}
