// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public class PyCompatibilityInspectionTest extends PyInspectionTestCase {

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return ourPy2Descriptor;
  }

  public void testExceptBlock() {
    doTest(LanguageLevel.PYTHON27);
  }

  public void testImportStatement() {
    doTest(LanguageLevel.PYTHON27);
  }

  public void testImportErrorCaught() {
    doTest(LanguageLevel.PYTHON27);
  }

  public void testStarExpression() {
    doTest(LanguageLevel.PYTHON35);
  }

  public void testBinaryExpression() {
    doTest(LanguageLevel.PYTHON27);
  }

  public void testNumericLiteralExpression() {
    doTest();
  }

  public void testStringLiteralExpression() {
    doTest();
  }

  public void testListCompExpression() {
    doTest();
  }

  public void testRaiseMultipleArgs() {
    doTest();
  }

  public void testRaiseFrom() {
    doTest(LanguageLevel.PYTHON34);
  }

  public void testReprExpression() {
    doTest();
  }

  // PY-42200
  public void testParenthesizedWithItems() {
    doTest(LanguageLevel.getLatest());
  }

  public void testPrintStatement() {
    doTest();
  }

  public void testFromImportStatement() {
    doTest();
  }

  public void testImportElement() {
    doTest();
  }

  public void testCallExpression() {
    doTest(LanguageLevel.PYTHON34);
  }

  public void testBasestring() {
    doTest();
  }

  public void testClassBaseList() {
    doTest();
  }

  // PY-7763
  public void testEllipsisAsStatementPy2() {
    doTest(LanguageLevel.PYTHON34);
  }

  // PY-8606
  public void testEllipsisInSubscriptionPy2() {
    doTest(LanguageLevel.PYTHON34);
  }

  // PY-15390
  public void testMatMul() {
    doTest(LanguageLevel.PYTHON35);
  }

  public void testAsyncAwait() {
    doTest(LanguageLevel.PYTHON35);
  }

  public void testDoubleStarUnpacking() {
    doTest(LanguageLevel.PYTHON35);
  }

  public void testArgumentsUnpackingGeneralizations() {
    doTest(LanguageLevel.PYTHON35);
  }

  // PY-19523
  public void testBz2Module() {
    doTest();
  }

  public void testUnderscoreBz2Module() {
    doTest();
  }

  // PY-19486
  public void testBackportedEnum() {
    doTest();
  }

  // PY-18880
  public void testBackportedTyping() {
    doTest();
  }

  public void testUnderscoresInNumericLiterals() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testVariableAnnotations() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-20770
  public void testYieldInsideAsyncDef() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-20770
  public void testAsyncComprehensions() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-20770
  public void testAwaitInComprehensions() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-16098
  public void testWarningAboutAsyncAndAwaitInPy36() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-22302
  public void testNoWarningAboutEllipsisInFunctionTypeComments() {
    doTest();
  }

  // PY-23355
  public void testNoWarningAboutStarredExpressionsInFunctionTypeComments() {
    doTest();
  }

  public void testBuiltinLong() {
    doTest();
  }

  // PY-26510
  public void testTryExceptEmptyRaise() {
    doTest();
  }

  // PY-26510
  public void testTryFinallyEmptyRaisePy2() {
    doTest();
  }

  // PY-26510
  public void testTryFinallyEmptyRaisePy3() {
    doTest(LanguageLevel.PYTHON34);
  }

  // PY-29763
  public void testTryExceptEmptyRaiseUnderFinallyPy2() {
    doTestByText("""
                   try:
                      something_that_raises_error1()
                   except BaseException as e:
                       raise
                   finally:
                       try:
                           something_that_raises_error2()
                       except BaseException as e:
                           raise  \s""");
  }

  // PY-15360
  public void testTrailingCommaAfterStarArgs() {
    doTest(LanguageLevel.PYTHON34);
  }

  // PY-36009
  public void testEqualitySignInFStrings() {
    doTest(LanguageLevel.PYTHON38);
  }

  public void testInputFromSixLib() {
    doTest(LanguageLevel.PYTHON27);
  }

  // PY-35512
  public void testPositionalOnlyParameters() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON38,
      () -> doTestByText(
        "def f(pos1, <warning descr=\"Python versions 2.7, 3.7 do not support positional-only parameters\">/</warning>, pos_or_kwd, *, kwd1):\n" +
        "    pass"
      )
    );
  }

  // PY-33886
  public void testAssignmentExpressions() {
    doTest(LanguageLevel.PYTHON38);
  }

  // PY-36003
  public void testContinueInFinallyBlock() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON38,
      () -> doTestByText("""
                           while True:
                             try:
                               print("a")
                             finally:
                               <warning descr="Python versions 2.7, 3.7 do not support 'continue' inside 'finally' clause">continue</warning>""")
    );
  }

  // PY-35961
  public void testUnpackingInNonParenthesizedTuplesInReturnAndYield() {
    doTest(LanguageLevel.PYTHON38);
  }

  // PY-41305
  public void testExpressionInDecorators() {
    doTest(LanguageLevel.PYTHON39);
  }

  // PY-53776
  public void testStarExpressionInIndexes() {
    doTest(LanguageLevel.PYTHON311);
  }

  // PY-53776
  public void testStarExpressionInTypeAnnotation() {
    doTest(LanguageLevel.PYTHON311);
  }

  // PY-60767
  public void testTypeAliasStatements() {
    doTest(LanguageLevel.PYTHON311);
  }

  // PY-60767
  public void testTypeParameterLists() {
    doTest(LanguageLevel.PYTHON311);
  }

  // PY-79967
  public void testTemplateStrings() {
    doTest(LanguageLevel.PYTHON313);
  }

  private void doTest(@NotNull LanguageLevel level) {
    runWithLanguageLevel(level, this::doTest);
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyCompatibilityInspection.class;
  }
}
