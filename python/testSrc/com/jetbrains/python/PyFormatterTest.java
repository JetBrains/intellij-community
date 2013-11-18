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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

/**
 * @author yole
 */
public class PyFormatterTest extends PyTestCase {
  public void testBlankLineBetweenMethods() {
    doTest();
  }

  public void testBlankLineAroundClasses() {
    CodeStyleSettingsManager.getSettings(myFixture.getProject()).BLANK_LINES_AROUND_CLASS = 2;
    doTest();
  }

  public void testSpaceAfterComma() {
    doTest();
  }

  public void testPep8ExtraneousWhitespace() {
    doTest();
  }

  public void testPep8Operators() {
    doTest();
  }

  public void testPep8KeywordArguments() {
    doTest();
  }

  public void testUnaryMinus() {
    doTest();
  }

  public void testBlankLineAfterImports() {
    doTest();
  }

  public void testBlankLineBeforeFunction() {
    doTest();
  }

  public void testStarArgument() {  // PY-1395
    doTest();
  }

  public void testDictLiteral() {  // PY-1461
    doTest();
  }

  public void testListAssignment() {  // PY-1522
    doTest();
  }

  public void testStarExpression() {  // PY-1523
    doTestPy3();
  }

  private void doTestPy3() {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON30);
    try {
      doTest();
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  public void testWrapTuple() {  // PY-1792
    doTest();
  }

  public void testSpaceAfterCommaWrappedLine() {  // PY-1065
    doTest();
  }

  public void testAlignInBinaryExpression() {
    doTest();
  }

  public void testAlignInStringLiteral() {
    doTest();
  }

  public void testComment() {  // PY-2108
    doTest();
  }

  public void testCommentBetweenClasses() { // PY-1598
    doTest();
  }

  public void testTwoLinesBetweenTopLevelClasses() { // PY-2765
    doTest();
  }

  public void testTwoLinesBetweenTopLevelFunctions() { // PY-2765
    doTest();
  }

  public void testSpecialSlice() {  // PY-1928
    doTest();
  }

  public void testNoWrapBeforeParen() {  // PY-3172
    doTest();
  }

  public void testTupleAssignment() {  // PY-4034 comment
    doTest();
  }

  public void testSpaceInMethodDeclaration() {  // PY-4241
    settings().SPACE_BEFORE_METHOD_PARENTHESES = true;
    doTest();
  }

  public void testOptionalAlignForMethodParameters() {  // PY-3995
    settings().ALIGN_MULTILINE_PARAMETERS = false;
    doTest();
  }

  public void testNoAlignForMethodArguments() {  // PY-3995
    settings().getCommonSettings(PythonLanguage.getInstance()).ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false;
    doTest();
  }

  public void testAlignForMethodArguments() {  // PY-3995
    doTest();
  }

  public void testLambdaColon() {
    doTest();
  }

  public void testInGenerator() {  // PY-5379
    doTest();
  }

  public void testIndentInGenerator() {  // PY-6219
    doTest();
  }

  public void testSpaceAroundDot() {  // PY-6908
    doTest();
  }

  public void testSetLiteralInArgList() {  // PY-6672
    settings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest();
  }

  public void testLiterals() {  // PY-6751
    doTest();
  }

  public void testTupleInArgList() {
    settings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest();
  }

  public void testAlignInBinaryExpressions() {
    doTest();
  }

  public void testFromImportRelative() {
    doTest();
  }

  public void testContinuationIndent() {
    doTest();
  }

  public void testBlankLineAfterDecorator() {
    doTest();
  }

  public void testSpaceAroundKeywords() {
    doTest();
  }

  public void testSpaceAfterReturn() {
    doTest();
  }

  public void testSpaceAfterRelativeImport() {  // PY-8112
    doTest();
  }

  public void testSpaceWithinBraces() {  // PY-8069
    settings().getCustomSettings(PyCodeStyleSettings.class).SPACE_WITHIN_BRACES = true;
    doTest();
  }

  public void testTupleClosingParen() {  // PY-7946
    doTest();
  }

  public void testBeforeTopLevelClass() {  // PY-7743
    doTest();
  }

  public void testPsiFormatting() { // IDEA-69724
    String initial =
      "def method_name(\n" +
      "   desired_impulse_response,\n" +
      " desired_response_parameters,\n" +
      " inverse_filter_length, \n" +
      " observed_impulse_response):\n" +
      " #  Extract from here to ...\n" +
      "   desired_impulse_response = {'dirac, 'gaussian', logistic_derivative'}\n" +
      "return desired,                o";

    final PsiFile file = PyElementGenerator.getInstance(myFixture.getProject()).createDummyFile(LanguageLevel.PYTHON30, initial);
    final PsiElement reformatted = CodeStyleManager.getInstance(myFixture.getProject()).reformat(file);

    String expected =
      "def method_name(\n" +
      "        desired_impulse_response,\n" +
      "        desired_response_parameters,\n" +
      "        inverse_filter_length,\n" +
      "        observed_impulse_response):\n" +
      "    #  Extract from here to ...\n" +
      "    desired_impulse_response = {'dirac, '\n" +
      "    gaussian\n" +
      "    ', logistic_derivative'}\n" +
      "    return desired, o";
    assertEquals(expected, reformatted.getText());
  }

  public void testWrapDefinitionWithLongLine() { // IDEA-92081
    settings().RIGHT_MARGIN = 30;
    settings().WRAP_LONG_LINES = true;
    doTest();
  }

  public void testWrapAssignment() {  // PY-8572
    settings().RIGHT_MARGIN = 120;
    settings().WRAP_LONG_LINES = false;
    doTest();
  }

  public void testIndentInSlice() {  // PY-8572
    settings().RIGHT_MARGIN = 120;
    settings().WRAP_LONG_LINES = false;
    doTest();
  }

  public void testIndentInComprehensions() {  // PY-8516
    settings().getCustomSettings(PyCodeStyleSettings.class).ALIGN_COLLECTIONS_AND_COMPREHENSIONS = false;
    doTest();
  }

  public void testAlignInGenerators() {  // PY-8822
    doTest();
  }

  public void testContinuationIndentForCallInStatementPart() {  // PY-8577
    doTest();
  }

  public void testIfConditionContinuation() {  // PY-8195
    doTest();
  }

  public void _testIndentInNestedCall() {  // PY-8195
    doTest();
  }

  public void testIndentAfterBackslash() {
    doTest();
  }

  public void testSpaceBeforeBackslash() {
    settings().getCustomSettings(PyCodeStyleSettings.class).SPACE_BEFORE_BACKSLASH = false;
    doTest();
  }

  public void testNewLineAfterColon() {
    settings().getCustomSettings(PyCodeStyleSettings.class).NEW_LINE_AFTER_COLON = true;
    doTest();
  }

  public void testNewLineAfterColonMultiClause() {
    doTest();
  }

  public void testLongWith() {  // PY-8743
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON27);
    try {
      doTest();
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  public void testSpaceInAnnotations() {  // PY-8961
    doTestPy3();
  }

  public void testWrapInBinaryExpression() {  // PY-9032
    settings().RIGHT_MARGIN = 80;
    doTest(true);
  }

  public void testSpaceWithinDeclarationParentheses() {  // PY-8818
    settings().SPACE_WITHIN_METHOD_PARENTHESES = true;
    doTest();
  }

  public void testWrapBeforeElse() {  // PY-10319
    doTest(true);
  }

  public void testSpacesInImportParentheses() {  // PY-11359
    doTest();
  }

  public void testWrapImports() {  // PY-9163
    settings().RIGHT_MARGIN = 80;
    doTest();
  }

  public void testCommentAfterBlock() {  // PY-9542
    doTest();
  }

  public void testWrapOnDot() {  // PY-6359
    doTest();
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean reformatText) {
    myFixture.configureByFile("formatter/" + getTestName(true) + ".py");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myFixture.getProject());
        PsiFile file = myFixture.getFile();
        if (reformatText) {
          codeStyleManager.reformatText(file, 0, file.getTextLength());
        }
        else {
          codeStyleManager.reformat(file);
        }
      }
    });
    myFixture.checkResultByFile("formatter/" + getTestName(true) + "_after.py");
  }

  private CodeStyleSettings settings() {
    return CodeStyleSettingsManager.getInstance().getSettings(myFixture.getProject());
  }
}
