/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import java.util.List;
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

  // PY-32067
  public void testAwaitInNonAsyncFunction() {
    var testPath = TEST_PATH + getTestName(true) + PyNames.DOT_PY;
    myFixture.testHighlighting(true, false, false, testPath);
    final List<IntentionAction> quickFixes = myFixture.filterAvailableIntentions("Convert");
    assertOneElement(quickFixes);
    myFixture.launchAction(quickFixes.get(0));
    myFixture.checkResultByFile(TEST_PATH + getTestName(true) + ".after.py");
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


  private void doTestWithLanguageLevel(LanguageLevel languageLevel, boolean checkWarnings, boolean checkInfos) {
    runWithLanguageLevel(languageLevel, () -> doTest(checkWarnings, checkInfos));
  }

  private void doTest(boolean checkWarnings, boolean checkInfos) {
    myFixture.testHighlighting(checkWarnings, checkInfos, false, TEST_PATH + getTestName(true) + PyNames.DOT_PY);
  }
}
