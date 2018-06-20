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
package com.jetbrains.python;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * Tests the "Join lines" handler.
 *
 * @author dcheryasov
 */
public class PyJoinLinesTest extends PyTestCase {
  private void doTest() {
    myFixture.configureByFile("joinLines/" + getTestName(false) + ".py");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_JOIN_LINES);
    myFixture.checkResultByFile("joinLines/" + getTestName(false) + "-after.py");
  }

  private void doTestWithCodeStyleSettings() {
    CodeStyleSettings settings = new CodeStyleSettings();
    settings.setRightMargin(PythonLanguage.getInstance(), 79);
    CodeStyle.setTemporarySettings(myFixture.getProject(), settings);

    doTest();
  }

  public void testBinaryOpBelow() {
    doTest();
  }

  public void testBinaryOp() {
    doTest();
  }

  public void testDictLCurly() {
    doTest();
  }

  public void testDictRCurly() {
    doTest();
  }

  public void testListLBracket() {
    doTest();
  }

  public void testList() {
    doTest();
  }

  public void testListRBracket() {
    doTest();
  }

  public void testStatementColon() {
    doTest();
  }

  public void testStatementComment() {
    doTest();
  }

  public void testStatementCommentStatement() {
    doTest();
  }

  public void testStringDifferentOneQuotes() {
    doTest();
  }

  public void testStringDifferentOneQuotesBelow() {
    doTest();
  }

  public void testStringOneQuoteEscEOL() {
    doTest();
  }

  public void testStringOneQuotePlainRaw() {
    doTest();
  }

  public void testStringOneQuotePlainU() {
    doTest();
  }

  public void testStringTripleQuotesDifferent() {
    doTest();
  }

  public void testStringTripleQuotes() {
    doTest();
  }

  public void testTupleLPar() {
    doTest();
  }

  public void testTuple() {
    doTest();
  }

  public void testTupleRPar() {
    doTest();
  }

  public void testTwoComments() {
    doTest();
  }

  // PY-7286
  public void testTwoComments2() {
    doTest();
  }

  public void testTwoStatements() {
    doTest();
  }

  public void testStringWithSlash() {
    doTest();
  }

  public void testListOfStrings() {
    doTest();
  }

  public void testLongExpression() {
    doTest();
  }

  public void testListComprehension() {
    doTest();
  }

  //PY-12205
  public void testStringsProducesTooLongLineAfterJoin() {
    doTestWithCodeStyleSettings();
  }

  //PY-12205
  public void testStringsWithDifferentQuotesTooLongToJoin() {
    doTestWithCodeStyleSettings();
  }

  //PY-12205
  public void testStringsWithTripleQuotesTooLongToJoin() {
    doTestWithCodeStyleSettings();
  }

  //PY-12205
  public void testCommentProducesTooLongLineAfterJoin() {

    doTestWithCodeStyleSettings();
  }

  // PY-15564
  public void testBackslashBetweenTargetsInImport() {
    doTest();
  }

  // PY-15564
  public void testBackslashBetweenTargetsInFromImport() {
    doTest();
  }
}
