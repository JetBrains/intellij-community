/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.breadcrumbs;

import com.intellij.ui.components.breadcrumbs.Crumb;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PyBreadcrumbsTest extends PyTestCase {

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/breadcrumbs";
  }

  public void testDictKey() {
    doTest();
  }

  public void testLambda() {
    doTest();
  }

  public void testLargeLambda() {
    doTest();
  }

  public void testTry() {
    doTest();
  }

  public void testExcept() {
    doTest();
  }

  public void testExceptAll() {
    doTest();
  }

  public void testExceptAs() {
    doTest();
  }

  public void testFinally() {
    doTest();
  }

  public void testTryElse() {
    doTest();
  }

  public void testIf() {
    doTest();
  }

  public void testElif() {
    doTest();
  }

  public void testIfElse() {
    doTest();
  }

  public void testFor() {
    doTest();
  }

  public void testAsyncFor() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  public void testForElse() {
    doTest();
  }

  public void testWhile() {
    doTest();
  }

  public void testWhileElse() {
    doTest();
  }

  public void testWith() {
    doTest();
  }

  public void testWithAs() {
    doTest();
  }

  public void testSeveralWith() {
    doTest();
  }

  public void testSeveralWithAs() {
    doTest();
  }

  public void testAsyncWith() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  public void testAsyncWithAs() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  public void testAsyncSeveralWith() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  public void testAsyncSeveralWithAs() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::doTest);
  }

  public void testClass() {
    doTest();
  }

  public void testMethod() {
    doTest();
  }

  public void testFunction() {
    doTest();
  }

  public void testFunctionBodySpaceCaret() {
    doTest();
  }

  public void testFunctionBodyCaret() {
    doTest();
  }

  public void testFunctionBodySpaceCaretNewLineFunction() {
    doTest();
  }

  public void testFunctionNewLineSpaceCaretNewLineFunction() {
    doTest();
  }

  public void testFunctionNewLineCaretNewLineFunction() {
    doTest();
  }

  public void testFunctionNewLineSpaceCaretSpaceFunction() {
    doTest();
  }

  public void testFunctionNewLineCaretSpaceFunction() {
    doTest();
  }

  public void testMultiLineDictLiteralCaretAfterFirstKeyValueExpression() {
    doTest();
  }

  public void testSingleLineDictLiteralCaretAfterFirstKeyValueExpression() {
    doTest();
  }

  public void testSingleLineDictLiteralCaretAfterComma() {
    doTest();
  }

  public void testSingleLineDictLiteralCaretAfterSecondKeyValueExpression() {
    doTest();
  }

  private void doTest() {
    final String testName = getTestName(true);

    myFixture.configureByFile(testName + ".py");

    final String breadcrumbsAndTooltips = getBreadcrumbsAndTooltips(myFixture.getBreadcrumbsAtCaret());

    assertSameLinesWithFile(getTestDataPath() + "/" + testName + "_crumbs.txt", breadcrumbsAndTooltips);
  }

  @NotNull
  private static String getBreadcrumbsAndTooltips(List<Crumb> crumbs) {
    return crumbs
      .stream()
      .flatMap(crumb -> Stream.of("Crumb:", crumb.getText(), "Tooltip:", crumb.getTooltip()))
      .collect(Collectors.joining("\n"));
  }
}