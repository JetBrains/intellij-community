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

import com.intellij.formatting.WrapType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

/**
 * @author yole
 */
public class PyFormatterTest extends PyTestCase {
  public void testBlankLineBetweenMethods() {
    doTest();
  }

  public void testBlankLineAroundClasses() {
    getCommonCodeStyleSettings().BLANK_LINES_AROUND_CLASS = 2;
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

  // PY-15701
  public void testNoBlankLinesAfterLocalImports() {
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

  public void testCommentInEmptyTuple() { //PY-11904
    doTest();
  }

  public void testTwoLinesBetweenTopLevelClasses() { // PY-2765
    doTest();
  }

  public void testTwoLinesBetweenTopLevelFunctions() { // PY-2765
    doTest();
  }

  // PY-9923
  public void testTwoLinesBetweenTopLevelDeclarationsWithComment() { // PY-9923
    doTest();
  }

  // PY-9923
  public void testTwoLinesBetweenTopLevelStatementAndDeclarationsWithComment() {
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
    getCommonCodeStyleSettings().SPACE_BEFORE_METHOD_PARENTHESES = true;
    doTest();
  }

  public void testOptionalAlignForMethodParameters() {  // PY-3995
    getCommonCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS = false;
    doTest();
  }

  public void testNoAlignForMethodArguments() {  // PY-3995
    getCommonCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false;
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
    getCommonCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest();
  }

  public void testLiterals() {  // PY-6751
    doTest();
  }

  public void testTupleInArgList() {
    getCommonCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
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

  public void testContinuationIndentInIndentingStatement() { // PY-9573
    doTest();
  }

  public void testContinuationIndentInIndentingStatement2() { // PY-11868
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
    getPythonCodeStyle().SPACE_WITHIN_BRACES = true;
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
      " # Extract from here to ...\n" +
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
      "    # Extract from here to ...\n" +
      "    desired_impulse_response = {'dirac, '\n" +
      "    gaussian\n" +
      "    ', logistic_derivative'}\n" +
      "    return desired, o";
    assertEquals(expected, reformatted.getText());
  }

  public void testWrapDefinitionWithLongLine() { // IDEA-92081
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 30);
    getCommonCodeStyleSettings().WRAP_LONG_LINES = true;
    doTest();
  }

  public void testWrapAssignment() {  // PY-8572
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 120);
    getCommonCodeStyleSettings().WRAP_LONG_LINES = false;
    doTest();
  }

  public void testIndentInSlice() {  // PY-8572
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 120);
    getCommonCodeStyleSettings().WRAP_LONG_LINES = false;
    doTest();
  }

  public void testIndentInComprehensions() {  // PY-8516
    getPythonCodeStyle().ALIGN_COLLECTIONS_AND_COMPREHENSIONS = false;
    doTest();
  }

  public void testAlignInGenerators() {  // PY-8822
    doTest();
  }

  public void testAlignInCallExpression() {
    doTest();
  }

  public void _testAlignInNestedCallInWith() { //PY-11337 TODO:
    doTest();
  }

  public void testContinuationIndentForCallInStatementPart() {  // PY-8577
    doTest();
  }

  public void testIfConditionContinuation() {  // PY-8195
    doTest();
  }

  public void _testIndentInNestedCall() {  // PY-11919 TODO: required changes in formatter to be able to make indent relative to block or alignment
    doTest();
  }

  public void testIndentAfterBackslash() {
    doTest();
  }

  public void testSpaceBeforeBackslash() {
    getPythonCodeStyle().SPACE_BEFORE_BACKSLASH = false;
    doTest();
  }

  public void testNewLineAfterColon() {
    getPythonCodeStyle().NEW_LINE_AFTER_COLON = true;
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

  // PY-8961, PY-16050
  public void testSpaceInAnnotations() {
    doTestPy3();
  }

  public void testWrapInBinaryExpression() {  // PY-9032
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 80);
    doTest(true);
  }

  public void testSpaceWithinDeclarationParentheses() {  // PY-8818
    getCommonCodeStyleSettings().SPACE_WITHIN_METHOD_PARENTHESES = true;
    doTest();
  }

  public void testWrapBeforeElse() {  // PY-10319
    doTest(true);
  }

  public void testSpacesInImportParentheses() {  // PY-11359
    doTest();
  }

  public void testWrapImports() {  // PY-9163
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 80);
    doTest();
  }

  public void testCommentAfterBlock() {  // PY-9542
    doTest();
  }

  public void testWrapOnDot() {  // PY-6359
    doTest();
  }

  public void testIndentParensInImport() { // PY-9075
    doTest();
  }

  public void testAlignInParenthesizedExpression() {
    doTest();
  }

  public void testAlignInParameterList() {
    doTest();
  }

  public void testAlignListComprehensionInDict() { //PY-10076
    doTest();
  }

  public void testParenthesisAroundGeneratorExpression() {
    doTest();
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean reformatText) {
    myFixture.configureByFile("formatter/" + getTestName(true) + ".py");
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
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

  // PY-12861
  public void testSpacesInsideParenthesisAreStripped() {
    doTest();
  }

  // PY-14838
  public void testNoAlignmentAfterDictHangingIndentInFunctionCall() {
    getCommonCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest();
  }

  // PY-13955
  public void testNoAlignmentAfterDictHangingIndentInFunctionCallOnTyping() {
    getCommonCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    final String testName = "formatter/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        myFixture.type("\n(");
      }
    });
    myFixture.checkResultByFile(testName + "_after.py");
  }

  // PY-12145
  public void testAlignmentOfClosingBraceInDictLiteralWhenNoHangingIndent() {
    doTest();
  }

  // PY-13004
  public void testAlignmentOfClosingParenthesisOfArgumentListWhenNoHangingIndent() {
    doTest();
  }

  // PY-14408
  public void testIndentsWithTabsInsideDictLiteral() {
    getCommonCodeStyleSettings().getIndentOptions().USE_TAB_CHARACTER = true;
    doTest();
  }

  // PY-12749
  public void testContinuationIndentIsNotUsedForNestedFunctionCallsInWithStatement() {
    doTest();
  }

  public void testAlignmentOfClosingParenthesisInNestedFunctionCallsWithSingleArgument() {
    doTest();
  }

  // PY-12748
  public void testIndentCommentariesInsideFromImportStatement() {
    doTest();
  }

  public void testClosingParenthesisInFromImportStatementWithNoHangingIndent() {
    doTest();
  }

  // PY-12932
  public void testCommentedCodeFragmentIgnored() {
    doTest();
  }

  // PY-12932
  public void testTrailingComment() {
    doTest();
  }

  // PY-12938
  public void testDoubleHashCommentIgnored() {
    doTest();
  }

  // PY-12938
  public void testDocCommentIgnored() {
    doTest();
  }

  // PY-12775
  public void testShebangCommentIgnored() {
    doTest();
  }

  // PY-13232
  public void testWhitespaceInsertedAfterHashSignInMultilineComment() {
    doTest();
  }

  /**
   * This test merely checks that call to {@link com.intellij.psi.codeStyle.CodeStyleManager#reformat(com.intellij.psi.PsiElement)}
   * is possible for Python sources.
   */
  public void testReformatOfSingleElementPossible() {
    myFixture.configureByFile("formatter/" + getTestName(true) + ".py");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), new Runnable() {
      @Override
      public void run() {
        final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        assertNotNull(elementAtCaret);
        final PyStatement statement = PsiTreeUtil.getParentOfType(elementAtCaret, PyStatement.class, false);
        assertNotNull(statement);
        final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myFixture.getProject());
        codeStyleManager.reformat(statement);
      }
    });
    myFixture.checkResultByFile("formatter/" + getTestName(true) + "_after.py");
  }

  // PY-11552
  public void testExtraBlankLinesBetweenMethodsAndAtTheEnd() {
    getCommonCodeStyleSettings().KEEP_BLANK_LINES_IN_DECLARATIONS = 1;
    doTest();
  }

  // PY-11552
  public void testTrailingBlankLinesWithBackslashesAtFileEnd() {
    doTest();
  }

  // PY-11552
  public void testTrailingBlankLinesWithBackslashesAtFunctionEnd() {
    doTest();
  }

  // PY-11552
  public void testTrailingBlankLinesWithBackslashesAtFunctionEndNoNewLine() {
    doTest();
  }

  // PY-11552
  public void testTrailingBlankLinesWithBackslashesMixed() {
    doTest();
  }

  // PY-11552
  public void testTrailingBlankLinesInEmptyFile() {
    doTest();
  }

  // PY-14962
  public void testAlignDictLiteralOnValue() {
    getPythonCodeStyle().DICT_ALIGNMENT = PyCodeStyleSettings.DICT_ALIGNMENT_ON_VALUE;
    doTest();
  }

  // PY-14962
  public void testAlignDictLiteralOnColon() {
    getPythonCodeStyle().DICT_ALIGNMENT = PyCodeStyleSettings.DICT_ALIGNMENT_ON_COLON;
    doTest();
  }

  // PY-14962
  public void testDictWrappingChopDownIfLong() {
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 80);
    getPythonCodeStyle().DICT_WRAPPING = WrapType.CHOP_DOWN_IF_LONG.getLegacyRepresentation();
    doTest();
  }

  // PY-14962
  public void testForceNewLineAfterLeftBraceInDict() {
    getPythonCodeStyle().DICT_NEW_LINE_AFTER_LEFT_BRACE = true;
    doTest();
  }

  // PY-14962
  public void testForceNewLineBeforeRightBraceInDict() {
    getPythonCodeStyle().DICT_NEW_LINE_BEFORE_RIGHT_BRACE = true;
    doTest();
  }

  // PY-15530
  public void testAlignmentInArgumentListWhereFirstArgumentIsEmptyCall() {
    doTest();
  }

  public void testAlignmentInListLiteralWhereFirstItemIsEmptyTuple() {
    doTest();
  }

  public void testHangingIndentInNamedArgumentValue() {
    doTest();
  }

  public void testHangingIndentInParameterDefaultValue() {
    doTest();
  }

  // PY-15171
  public void testHangingIndentInKeyValuePair() {
    doTest();
  }

  public void testDoNotDestroyAlignment_OnPostponedFormatting() throws Exception {
    getPythonCodeStyle().DICT_ALIGNMENT = PyCodeStyleSettings.DICT_ALIGNMENT_ON_COLON;
    doTest();
  }

  public void testAlignmentOfEmptyCollectionLiterals() {
    doTest();
  }
}
