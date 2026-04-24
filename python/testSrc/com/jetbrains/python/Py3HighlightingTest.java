// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class Py3HighlightingTest extends PyTestCase {

  @NotNull
  private static final String TEST_PATH = "/highlighting/";

  // PY-20770
  public void testNonEmptyReturnInsideAsyncDef() {
    doTest(true, false);
  }

  // PY-48010
  public void testSingleStarPatternOutsideSequencePattern() {
    doTest(true, false);
  }

  // PY-48010
  public void testDoubleStarPatternOutsideMappingPattern() {
    doTest(true, false);
  }

  // PY-48010
  public void testIllegalNumericLiteralPattern() {
    doTest(true, false);
  }

  // PY-48010
  public void testIllegalMappingKeyPattern() {
    doTest(true, false);
  }

  // PY-48010
  public void testIrrefutablePatternIsNotLastOrPatternAlternative() {
    doTest(true, false);
  }

  // PY-48010
  public void testIrrefutablePatternIsNotInLastCaseClause() {
    doTest(true, false);
  }

  // PY-48010
  public void testOrPatternAlternativesBindDifferentNames() {
    doTest(true, false);
  }

  // PY-48010
  public void testAttributeNameRepeatedInClassPattern() {
    doTest(true, false);
  }

  // PY-48010
  public void testPatternBindsNameMultipleTimes() {
    doTest(true, false);
  }

  // PY-48010
  public void testDuplicatedSingleStarPattern() {
    doTest(true, false);
  }

  // PY-48010
  public void testDuplicatedDoubleStarPattern() {
    doTest(true, false);
  }

  // EA-350132
  public void testIncompleteMatchStatementWithoutCaseClauses() {
    doTest(true, false);
  }

  // PY-49774
  public void testMatchStatementBefore310() {
    doTestWithLanguageLevel(LanguageLevel.PYTHON39, true, true);
  }

  // PY-44974
  public void testBitwiseOrUnionInOlderVersionsError() {
    doTestWithLanguageLevel(LanguageLevel.PYTHON39, false, false);
  }

  // PY-44974
  public void testBitwiseOrUnionInOlderVersionsErrorIsInstance() {
    doTestWithLanguageLevel(LanguageLevel.PYTHON39, false, false);
  }

  // PY-49697
  public void testNoErrorMetaClassOverloadBitwiseOrOperator() {
    doTestWithLanguageLevel(LanguageLevel.PYTHON39, false, false);
  }

  // PY-49697
  public void testNoErrorMetaClassOverloadBitwiseOrOperatorReturnTypesUnion() {
    doTestWithLanguageLevel(LanguageLevel.PYTHON39, false, false);
  }

  // PY-51329
  public void testNoErrorMetaClassOverloadBitwiseOrChain() {
    doTestWithLanguageLevel(LanguageLevel.PYTHON39, false, false);
  }

  public void testNoErrorMetaClassOverloadBitwiseRorChain() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON39,
      () -> {
        myFixture.configureByText(
          "test.py", """
            class M(type):
                def __ror__(self, other):
                    return other
            
            class A(metaclass=M):
                ...
            class B(metaclass=M):
                ...
            class C(metaclass=M):
                ...
            
            print(A | B | C)
            """);
        myFixture.testHighlighting(false, false, true);
      }
    );
  }

  // PY-32067
  public void testAwaitInNonAsyncFunction() {
    doHighlightingQuickfixTest("Convert to async function");
  }

  // PY-79522
  public void testAsyncWithInNonAsyncFunction() {
    doHighlightingQuickfixTest("Convert to async function");
  }

  // PY-79522
  public void testAsyncForInNonAsyncFunction() {
    doHighlightingQuickfixTest("Convert to async function");
  }

  // PY-79522
  public void testAsyncListComprehensionInNonAsyncFunction() {
    doHighlightingQuickfixTest("Convert to async function");
  }

  // PY-79522
  public void testAsyncDictComprehensionInNonAsyncFunction() {
    doHighlightingQuickfixTest("Convert to async function");
  }

  // PY-79522
  public void testAsyncSetComprehensionInNonAsyncFunction() {
    doHighlightingQuickfixTest("Convert to async function");
  }

  // PY-32067
  public void testAwaitInLoopInNonAsyncFunction() {
    doTest(false, false);
  }

  // PY-32067
  public void testAwaitInComprehensionInNonAsyncFunction() {
    doTest(false, false);
  }

  // PY-32067
  public void testAwaitInNonAsyncInnerFunctionOfAsyncFunction() {
    doTest(false, false);
  }

  // PY-32067
  public void testAwaitInAsyncIterator() {
    doTest(false, false);
  }

  // PY-32067
  public void testAwaitInFunctionDefaultArg() {
    doTest(false, false);
  }

  // PY-32067
  public void testAwaitOutsideFunction() {
    doTest(false, false);
  }

  // PY-32067
  public void testAwaitInNonAsyncFunctionPy27() {
    doTestWithLanguageLevel(LanguageLevel.PYTHON27, false, false);
  }

  // PY-32067
  public void testAwaitInDefaultArgOfInnerNonAsyncFunction() {
    doTest(false, false);
  }

  // PY-32067
  public void testAwaitInDefaultArgOfFunctionDecorator() {
    doTest(false, false);
  }

  // PY-32067
  public void testAwaitInsideAwaitExpression() {
    doTest(false, false);
  }

  // PY-62670
  public void testAwaitInsideJupyterNotebook() {
    doTest(false, false, ".ipynb");
  }

  // PY-83160
  public void testStarInTypeAnnotationString() {
    doTest(true, false);
  }

  private void doTestWithLanguageLevel(LanguageLevel languageLevel, boolean checkWarnings, boolean checkInfos) {
    runWithLanguageLevel(languageLevel, () -> doTest(checkWarnings, checkInfos));
  }

  private void doHighlightingQuickfixTest(String hint) {
    var testPath = TEST_PATH + getTestName(true) + PyNames.DOT_PY;
    var testPathAfter = TEST_PATH + getTestName(true) + ".after.py";
    myFixture.testHighlighting(true, false, false, testPath);
    var quickFix = myFixture.findSingleIntention(hint);
    myFixture.launchAction(quickFix);
    myFixture.testHighlighting(true, false, false, testPathAfter);
  }

  private void doTest(boolean checkWarnings, boolean checkInfos) {
    doTest(checkWarnings, checkInfos, PyNames.DOT_PY);
  }

  private void doTest(boolean checkWarnings, boolean checkInfos, String extension) {
    myFixture.testHighlighting(checkWarnings, checkInfos, false, TEST_PATH + getTestName(true) + extension);
  }
}
