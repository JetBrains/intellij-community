// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.formatting.WrapType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.fixture.PythonCommonTestCase;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyStatement;
import org.jetbrains.annotations.NotNull;

public abstract class PythonCommonFormatterTest extends PythonCommonTestCase {
  protected static final LanguageLevel LANGUAGE_LEVEL = LanguageLevel.getLatest();

  @NotNull
  private PyCodeStyleSettings getPythonCodeStyleSettings() {
    return getCodeStyleSettings().getCustomSettings(PyCodeStyleSettings.class);
  }

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

  // PY-35936
  public void testPep8MultipleStatementsOnOneLine() {
    getPythonCodeStyleSettings().NEW_LINE_AFTER_COLON = true;
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
    doTest();
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

  public void testDefaultWrappingForMethodParameters() {  // PY-33060
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 80);
    doTest();
  }

  public void testDefaultWrappingWithNewLineParensForMethodParameters() {  // PY-33060
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 80);
    getCommonCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS = false;
    getCommonCodeStyleSettings().METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    getCommonCodeStyleSettings().METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    doTest();
  }

  public void testWrappingChopDownIfLongForMethodParameters() {  // PY-33060
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 80);
    getCommonCodeStyleSettings().METHOD_PARAMETERS_WRAP = WrapType.CHOP_DOWN_IF_LONG.getLegacyRepresentation();
    doTest();
  }

  public void testWrappingChopDownIfLongWithNewLineParensForMethodParameters() {  // PY-33060
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 80);
    getCommonCodeStyleSettings().METHOD_PARAMETERS_WRAP = WrapType.CHOP_DOWN_IF_LONG.getLegacyRepresentation();
    getCommonCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS = false;
    getCommonCodeStyleSettings().METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    getCommonCodeStyleSettings().METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    doTest();
  }

  public void testNoAlignForMethodArguments() {  // PY-3995
    getCommonCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false;
    doTest();
  }

  public void testAlignForMethodArguments() {  // PY-3995
    doTest();
  }

  public void testDefaultWrappingForCallArguments() {  // PY-33060
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 80);
    doTest();
  }

  public void testDefaultWrappingWithNewLineParensForCallArguments() {  // PY-33060
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 80);
    getCommonCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false;
    getCommonCodeStyleSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    getCommonCodeStyleSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    doTest();
  }

  public void testWrappingChopDownIfLongForCallArguments() {  // PY-33060
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 80);
    getCommonCodeStyleSettings().CALL_PARAMETERS_WRAP = WrapType.CHOP_DOWN_IF_LONG.getLegacyRepresentation();
    doTest();
  }

  public void testWrappingChopDownIfLongWithNewLineParensForCallArguments() {  // PY-33060
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 80);
    getCommonCodeStyleSettings().CALL_PARAMETERS_WRAP = WrapType.CHOP_DOWN_IF_LONG.getLegacyRepresentation();
    getCommonCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false;
    getCommonCodeStyleSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    getCommonCodeStyleSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
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
    getPythonCodeStyleSettings().SPACE_WITHIN_BRACES = true;
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
      """
        def method_name(
           desired_impulse_response,
         desired_response_parameters,
         inverse_filter_length,\s
         observed_impulse_response):
         # Extract from here to ...
           desired_impulse_response = {'dirac, 'gaussian', logistic_derivative'}
        return desired,                o""";

    final PsiFile file = PyElementGenerator.getInstance(myFixture.getProject()).createDummyFile(LanguageLevel.getLatest(), initial);
    final PsiElement reformatted = CodeStyleManager.getInstance(myFixture.getProject()).reformat(file);

    String expected =
      """
        def method_name(
                desired_impulse_response,
                desired_response_parameters,
                inverse_filter_length,
                observed_impulse_response):
            # Extract from here to ...
            desired_impulse_response = {'dirac, '
            gaussian
            ', logistic_derivative'}
            return desired, o
        """;
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
    getPythonCodeStyleSettings().ALIGN_COLLECTIONS_AND_COMPREHENSIONS = false;
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
    getPythonCodeStyleSettings().SPACE_BEFORE_BACKSLASH = false;
    doTest();
  }

  public void testNewLineAfterColon() {
    getPythonCodeStyleSettings().NEW_LINE_AFTER_COLON = true;
    doTest();
  }

  public void testNewLineAfterColonMultiClause() {
    doTest();
  }

  public void testLongWith() {  // PY-8743
    doTest();
  }

  // PY-8961, PY-16050
  public void testSpaceInAnnotations() {
    doTest();
  }

  // PY-15791
  public void testForceSpacesAroundEqualSignInAnnotatedParameter() {
    doTest();
  }

  public void testWrapInBinaryExpression() {  // PY-9032
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 80);
    doTest(true);
  }

  public void testSpaceWithinDeclarationParentheses() {  // PY-8818
    getCommonCodeStyleSettings().SPACE_WITHIN_METHOD_PARENTHESES = true;
    getCommonCodeStyleSettings().SPACE_WITHIN_EMPTY_METHOD_PARENTHESES = false;
    doTest();
  }

  // PY-21598
  public void testSpaceBetweenParenthesesInEmptyParameterList() {
    getCommonCodeStyleSettings().SPACE_WITHIN_METHOD_PARENTHESES = false;
    getCommonCodeStyleSettings().SPACE_WITHIN_EMPTY_METHOD_PARENTHESES = true;
    doTest();
  }

  public void testSpaceWithingCallParentheses() {
    getCommonCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    getCommonCodeStyleSettings().SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = false;
    doTest();
  }

  // PY-21598
  public void testSpaceBetweenParenthesesInEmptyArgumentList() {
    getCommonCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;
    getCommonCodeStyleSettings().SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = true;
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
    doTest(reformatText, "formatter/", ".py");
  }

  private void doTest(final boolean reformatText, final String filePath, final String fileExtension) {
    myFixture.configureByFile(filePath + getTestName(true) + fileExtension);
    WriteCommandAction.runWriteCommandAction(null, () -> {
      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myFixture.getProject());
      PsiFile file = myFixture.getFile();
      if (reformatText) {
        codeStyleManager.reformatText(file, 0, file.getTextLength());
      }
      else {
        codeStyleManager.reformat(file);
      }
    });
    myFixture.checkResultByFile(filePath + getTestName(true) + "_after" + fileExtension);
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
    myFixture.type("\n(");
    myFixture.checkResultByFile(testName + "_after.py");
  }

  // PY-12145
  public void testAlignmentOfClosingBraceInDictLiteralWhenNoHangingIndent() {
    doTest();
  }

  public void testNoAlignmentClosingBraceInDictLiteralWhenOpeningBraceIsForcedOnNewLine() {
    getPythonCodeStyleSettings().DICT_NEW_LINE_AFTER_LEFT_BRACE = true;
    doTest();
  }

  // PY-13004
  public void testAlignmentOfClosingParenthesisOfArgumentListWhenNoHangingIndent() {
    doTest();
  }

  // PY-14408
  public void testIndentsWithTabsInsideDictLiteral() {
    getIndentOptions().USE_TAB_CHARACTER = true;
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

  //DS-1584
  public void testSpaceIPythonMagicCommands() { doTest(); }

  public void testSpaceIPythonMagicCommandsJupyter() { doTest(false, "formatter/jupyter/", ".ipynb"); }

  //DS-2583
  public void testSpaceShellCommandsJupyter() { doTest(false, "formatter/jupyter/", ".ipynb"); }

  //DS-4478
  public void testSpaceShellCommandsPathJupyter() { doTest(false, "formatter/jupyter/", ".ipynb"); }

  //DS-5427
  public void testMagicPath() { doTest(false, "formatter/jupyter/", ".ipynb"); }

  //DS-5427
  public void testMagicPathEmptyCells() { doTest(false, "formatter/jupyter/", ".ipynb"); }

  //DS-5427
  public void testMagicPathComment() { doTest(false, "formatter/jupyter/", ".ipynb"); }


  /**
   * This test merely checks that call to {@link com.intellij.psi.codeStyle.CodeStyleManager#reformat(com.intellij.psi.PsiElement)}
   * is possible for Python sources.
   */
  public void testReformatOfSingleElementPossible() {
    myFixture.configureByFile("formatter/" + getTestName(true) + ".py");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      assertNotNull(elementAtCaret);
      final PyStatement statement = PsiTreeUtil.getParentOfType(elementAtCaret, PyStatement.class, false);
      assertNotNull(statement);
      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myFixture.getProject());
      codeStyleManager.reformat(statement);
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
    getPythonCodeStyleSettings().DICT_ALIGNMENT = PyCodeStyleSettings.DICT_ALIGNMENT_ON_VALUE;
    doTest();
  }

  // PY-22272
  public void testAlightDictLiteralOnValueSubscriptionsAndSlices() {
    getPythonCodeStyleSettings().DICT_ALIGNMENT = PyCodeStyleSettings.DICT_ALIGNMENT_ON_VALUE;
    doTest();
  }

  // PY-14962
  public void testAlignDictLiteralOnColon() {
    getPythonCodeStyleSettings().DICT_ALIGNMENT = PyCodeStyleSettings.DICT_ALIGNMENT_ON_COLON;
    doTest();
  }

  // PY-14962
  public void testDictWrappingChopDownIfLong() {
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 80);
    getPythonCodeStyleSettings().DICT_WRAPPING = WrapType.CHOP_DOWN_IF_LONG.getLegacyRepresentation();
    doTest();
  }

  // PY-14962
  public void testForceNewLineAfterLeftBraceInDict() {
    getPythonCodeStyleSettings().DICT_NEW_LINE_AFTER_LEFT_BRACE = true;
    doTest();
  }

  // PY-14962
  public void testForceNewLineBeforeRightBraceInDict() {
    getPythonCodeStyleSettings().DICT_NEW_LINE_BEFORE_RIGHT_BRACE = true;
    doTest();
  }

  public void testForceNewLineAfterLeftParenInMethodParameters() {  // PY-33060
    getCommonCodeStyleSettings().METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    doTest();
  }

  public void testForceNewLineBeforeRightParenInMethodParameters() {  // PY-33060
    getCommonCodeStyleSettings().METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    doTest();
  }

  public void testForceNewLineBeforeRightParenNoAlignInMethodParameters() {  // PY-33060
    getCommonCodeStyleSettings().METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    getCommonCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS = false;
    doTest();
  }

  public void testForceNewLineAfterLeftParenInCallArguments() {  // PY-33060
    getCommonCodeStyleSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    doTest();
  }

  public void testForceNewLineBeforeRightParenInCallArguments() {  // PY-33060
    getCommonCodeStyleSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    doTest();
  }

  public void testForceNewLineBeforeRightParenNoAlignInCallArguments() {  // PY-33060
    getCommonCodeStyleSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    getCommonCodeStyleSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false;
    doTest();
  }

  // PY-17674
  public void testForceNewLineBeforeRightBraceInDictAfterColon() {
    getPythonCodeStyleSettings().DICT_NEW_LINE_BEFORE_RIGHT_BRACE = true;
    doTest();
  }

  // PY-16393
  public void testHangingIndentDetectionIgnoresComments() {
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

  public void testDoNotDestroyAlignment_OnPostponedFormatting() {
    getPythonCodeStyleSettings().DICT_ALIGNMENT = PyCodeStyleSettings.DICT_ALIGNMENT_ON_COLON;
    doTest();
  }

  public void testAlignmentOfEmptyCollectionLiterals() {
    doTest();
  }

  // PY-17593
  public void testBlanksBetweenImportsPreservedWithoutOptimizeImports() {
    doTest();
  }

  // PY-33060
  public void testContinuationIndentBeforeFunctionParameters() {
    doTest();
  }

  // PY-33060
  public void testNoContinuationIndentBeforeFunctionParameters() {
    getPythonCodeStyleSettings().USE_CONTINUATION_INDENT_FOR_PARAMETERS = false;
    doTest();
  }

  // PY-17979, PY-13304
  public void testContinuationIndentBeforeFunctionArguments() {
    getPythonCodeStyleSettings().USE_CONTINUATION_INDENT_FOR_ARGUMENTS = true;
    doTest();
  }

  // PY-20909
  public void testContinuationIndentForCollectionsAndComprehensions() {
    getPythonCodeStyleSettings().USE_CONTINUATION_INDENT_FOR_COLLECTION_AND_COMPREHENSIONS = true;
    doTest();
  }

  // PY-20909
  public void testContinuationIndentForCollectionsAndComprehensionsHangingIndentOfClosingBrace() {
    getPythonCodeStyleSettings().USE_CONTINUATION_INDENT_FOR_COLLECTION_AND_COMPREHENSIONS = true;
    getPythonCodeStyleSettings().HANG_CLOSING_BRACKETS = true;
    doTest();
  }

  // PY-18265
  public void testNoSpaceAroundPowerOperator() {
    getPythonCodeStyleSettings().SPACE_AROUND_POWER_OPERATOR = false;
    doTest();
  }

  // PY-20392
  public void testSpaceAfterTrailingCommaInDictLiterals() {
    doTest();
  }

  // PY-20392
  public void testSpaceAfterTrailingCommaIfNoSpaceAfterCommaButWithinBracesOrBrackets() {
    getPythonCodeStyleSettings().SPACE_WITHIN_BRACES = true;
    getCommonCodeStyleSettings().SPACE_WITHIN_BRACKETS = true;
    getCommonCodeStyleSettings().SPACE_AFTER_COMMA = false;
    doTest();
  }

  // PY-10182
  public void testHangClosingParenthesisInFromImport() {
    // Shouldn't affect the result
    getPythonCodeStyleSettings().ALIGN_MULTILINE_IMPORTS = false;
    getPythonCodeStyleSettings().HANG_CLOSING_BRACKETS = true;
    doTest();
  }

  // PY-10182
  public void testHangClosingParenthesisInFunctionCall() {
    getPythonCodeStyleSettings().HANG_CLOSING_BRACKETS = true;
    doTest();
  }

  // PY-10182
  public void testHangClosingParenthesisInFunctionDefinition() {
    getPythonCodeStyleSettings().HANG_CLOSING_BRACKETS = true;
    doTest();
  }

  // PY-10182
  public void testHangClosingBracketsInCollectionLiterals() {
    getPythonCodeStyleSettings().HANG_CLOSING_BRACKETS = true;
    doTest();
  }

  // PY-15874
  public void testHangClosingOffComprehensionsAndGeneratorExpressions() {
    getPythonCodeStyleSettings().HANG_CLOSING_BRACKETS = false;
    doTest();
  }

  // PY-15874
  public void testHangClosingOnComprehensionsAndGeneratorExpressions() {
    getPythonCodeStyleSettings().HANG_CLOSING_BRACKETS = true;
    doTest();
  }

  // PY-20633
  public void testFromImportWrappingChopDownIfLong() {
    getPythonCodeStyleSettings().FROM_IMPORT_WRAPPING = WrapType.CHOP_DOWN_IF_LONG.getLegacyRepresentation();
    getCodeStyleSettings().setRightMargin(PythonLanguage.INSTANCE, 30);
    doTest();
  }

  // PY-20633
  public void testFromImportParenthesesPlacement() {
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS = true;
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = true;
    getCommonCodeStyleSettings().SPACE_AFTER_COLON = true;
    getCodeStyleSettings().setRightMargin(PythonLanguage.INSTANCE, 35);
    doTest();
  }

  // PY-20633
  public void testFromImportParenthesesPlacementHangClosingParenthesis() {
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS = true;
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = true;
    getPythonCodeStyleSettings().HANG_CLOSING_BRACKETS = true;
    getCommonCodeStyleSettings().SPACE_AFTER_COLON = true;
    getCodeStyleSettings().setRightMargin(PythonLanguage.INSTANCE, 35);
    doTest();
  }

  // PY-20633
  public void testFromImportForceParenthesesIfMultiline() {
    getCodeStyleSettings().setRightMargin(PythonLanguage.INSTANCE, 30);
    getPythonCodeStyleSettings().FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE = true;
    doTest();
  }

  // PY-20633
  // See http://docs.pylonsproject.org/en/latest/community/codestyle.html
  public void testPyramidFromImportFormatting() {
    getPythonCodeStyleSettings().FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE = true;
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS = true;
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = true;
    getPythonCodeStyleSettings().FROM_IMPORT_WRAPPING = WrapType.ALWAYS.getLegacyRepresentation();
    getPythonCodeStyleSettings().FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE = true;
    getPythonCodeStyleSettings().HANG_CLOSING_BRACKETS = true;
    doTest();
  }

  // PY-9764
  public void testFromImportTrailingCommaWithParentheses() {
    getCodeStyleSettings().setRightMargin(PythonLanguage.INSTANCE, 30);
    getPythonCodeStyleSettings().FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE = true;
    getPythonCodeStyleSettings().FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE = true;
    doTest();
  }

  // PY-9764
  public void testFromImportTrailingCommaWithoutParentheses() {
    getCodeStyleSettings().setRightMargin(PythonLanguage.INSTANCE, 30);
    getPythonCodeStyleSettings().FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE = false;
    getPythonCodeStyleSettings().FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE = true;
    doTest();
  }

  // PY-21931
  public void testSpacesAroundElseInConditionalExpression() {
    doTest();
  }

  // PY-20970
  public void testSpacesAfterNonlocal() {
    doTest();
  }

  // PY-21515
  public void testSpacesBeforeFromImportSource() {
    doTest();
  }

  public void testSpacesAfterFromInYieldFrom() {
    doTest();
  }

  // PY-24220
  public void testBlankLinesAfterTopLevelImportsBeforeClass() {
    getCommonCodeStyleSettings().BLANK_LINES_AFTER_IMPORTS = 5;
    doTest();
  }

  // PY-24220
  public void testBlankLinesAfterTopLevelImportsBeforeClassWithPrecedingComments() {
    getCommonCodeStyleSettings().BLANK_LINES_AFTER_IMPORTS = 5;
    doTest();
  }

  // PY-25356
  public void testCommentsSpacing() {
    doTest();
  }

  // PY-19705
  public void testBlankLinesAroundFirstMethod() {
    getPythonCodeStyleSettings().BLANK_LINES_BEFORE_FIRST_METHOD = 1;
    doTest();
  }

  // PY-21823
  public void testSliceAlignment() {
    doTest();
  }

  // PY-15051
  public void testTrailingBlockCommentsIndentationPreserved() {
    doTest();
  }

  public void testMultilineIfConditionKeywordAtEnd() {
    doTest();
  }

  // PY-21328
  public void testMultilineIfConditionLessComparisonsKeywordAtEnd() {
    doTest();
  }

  public void testMultilineIfConditionKeywordAtStart() {
    doTest();
  }

  public void testMultilineIfConditionInParenthesesKeywordAtEnd() {
    doTest();
  }

  public void testMultilineIfConditionInParenthesesNegatedKeywordAtEnd() {
    doTest();
  }

  public void testMultilineIfConditionInParenthesesKeywordAtEndSecondOperandIsReference() {
    doTest();
  }

  public void testMultilineIfConditionInParenthesesKeywordAtStart() {
    doTest();
  }

  public void testMultilineIfConditionNestedExpressions() {
    doTest();
  }

  public void testMultilineIfConditionInParenthesesNestedExpressions() {
    doTest();
  }

  public void testMultilineElifCondition() {
    doTest();
  }

  public void testMultilineElifConditionInParentheses() {
    doTest();
  }

  // PY-22035
  public void testMultilineIfConditionComplex() {
    doTest();
  }

  // PY-24160
  public void testMultilineIfConditionInParenthesesHangingIndent() {
    doTest();
  }

  public void testMultilineBinaryExpressionInsideGenerator() {
    doTest();
  }

  public void testNotParenthesisedBinaryExpressions() {
    doTest();
  }

  public void testGluedStringLiteralInParentheses() {
    getPythonCodeStyleSettings().ALIGN_COLLECTIONS_AND_COMPREHENSIONS = false;
    doTest();
    getPythonCodeStyleSettings().ALIGN_COLLECTIONS_AND_COMPREHENSIONS = true;
    doTest();
  }

  public void testVariableAnnotations() {
    doTest();
  }

  // PY-27266
  public void testChainedMethodCallsInParentheses() {
    doTest();
  }

  // PY-27266
  public void testChainedAttributeAccessInParentheses() {
    doTest();
  }

  public void testMultilineFStringExpressions() {
    doTest();
  }

  // PY-33656
  public void testBackslashGluedFStringNodesAlignment() {
    doTest();
  }

  // PY-27615
  public void testFStringFragmentWrappingSplitInsideExpressionWithBackslash() {
    PyCodeInsightSettings.getInstance().PARENTHESISE_ON_ENTER = false;
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 20);
    getCommonCodeStyleSettings().WRAP_LONG_LINES = true;
    doTest();
  }


  public void testFStringFragmentWrappingSplitInsideExpressionWithParentheses() {
    PyCodeInsightSettings.getInstance().PARENTHESISE_ON_ENTER = true;
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 20);
    getCommonCodeStyleSettings().WRAP_LONG_LINES = true;
    doTest();
  }

  // PY-27615
  public void testFStringFragmentWrappingSplitInsideNestedExpressionWithBackslash() {
    PyCodeInsightSettings.getInstance().PARENTHESISE_ON_ENTER = false;
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 20);
    getCommonCodeStyleSettings().WRAP_LONG_LINES = true;
    doTest();
  }

  // TODO enable after PY-61453 fixed
  //public void testFStringFragmentWrappingSplitInsideNestedExpressionWithParentheses() {
  //  PyCodeInsightSettings.getInstance().PARENTHESISE_ON_ENTER = true;
  //  getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 20);
  //  getCommonCodeStyleSettings().WRAP_LONG_LINES = true;
  //  doTest();
  //}

  // PY-40778
  public void testFStringSpacesBetweenFragmentAndExpressionBracesPreserved() {
    doTest();
  }

  // PY-31991
  public void testSpacesAroundFStringFragmentExpressionStripped() {
    doTest();
  }

  // PY-36009
  public void testSpacesAroundEqualsSignInFStringFragment() {
    doTest();
  }

  // PY-35975
  public void testSpacesAroundColonEqInAssignmentExpression() {
    doTest();
  }

  // PY-23475
  public void testModuleLevelDunderWithImports() {
    doTest();
  }

  // PY-48009
  public void testIndentOfCaseClausesInsideMatchStatement() {
    doTest();
  }

  // PY-48009
  public void testIndentOfCommentsInsideMatchStatement() {
    doTest();
  }

  // PY-49167
  public void testNoSpacesInsideStarPatterns() {
    doTest();
  }

  // PY-48009
  public void testSpacesWithinBracketsInSequencePatterns() {
    getCommonCodeStyleSettings().SPACE_WITHIN_BRACKETS = true;
    doTest();
  }

  // PY-48009
  public void testSpacesWithinBracesInMappingPatterns() {
    getPythonCodeStyleSettings().SPACE_WITHIN_BRACES = true;
    doTest();
  }

  // PY-48009
  public void testSpacesWithinParenthesesInClassPatterns() {
    getCommonCodeStyleSettings().SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = true;
    getCommonCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    doTest();
  }

  // PY-48009
  public void testSpacesBeforeParenthesesInClassPatterns() {
    getCommonCodeStyleSettings().SPACE_BEFORE_METHOD_CALL_PARENTHESES = true;
    doTest();
  }

  // PY-48009
  public void testSpacesBeforeAndAfterCommasInPatterns() {
    getCommonCodeStyleSettings().SPACE_BEFORE_COMMA = true;
    getCommonCodeStyleSettings().SPACE_AFTER_COMMA = false;
    doTest();
  }

  // PY-48009
  public void testSpacesBeforeAndAfterColonsInPatterns() {
    getPythonCodeStyleSettings().SPACE_BEFORE_PY_COLON = true;
    getPythonCodeStyleSettings().SPACE_AFTER_PY_COLON = false;
    doTest();
  }

  // PY-48009
  public void testItemAlignmentInSequencePatterns() {
    doTest();
  }

  // PY-48009
  public void testItemAlignmentInNestedSequencePatterns() {
    doTest();
  }

  // PY-48009
  public void testItemIndentInSequencePatterns() {
    doTest();
  }

  // PY-48009
  public void testHangingClosingBracketInSequencePatterns() {
    getPythonCodeStyleSettings().HANG_CLOSING_BRACKETS = true;
    doTest();
  }

  // PY-48009
  public void testItemAlignmentInMappingPatterns() {
    doTest();
  }

  // PY-48009
  public void testItemIndentInMappingPatterns() {
    doTest();
  }

  // PY-48009
  public void testHangingClosingBracketInMappingPatterns() {
    getPythonCodeStyleSettings().HANG_CLOSING_BRACKETS = true;
    doTest();
  }

  // PY-48009
  public void testAttributeAlignmentInClassPatterns() {
    doTest();
  }

  // PY-48009
  public void testAttributeIndentInClassPatterns() {
    doTest();
  }

  // PY-48009
  public void testHangingClosingBracketInClassPatterns() {
    getPythonCodeStyleSettings().HANG_CLOSING_BRACKETS = true;
    doTest();
  }

  // PY-48009
  public void testStringElementAlignmentInLiteralPatterns() {
    doTest();
  }

  // PY-48009
  public void testSpacesAroundAsKeywordInPatterns() {
    doTest();
  }

  // PY-48009
  public void testSpacesAfterMatchAndCaseKeywords() {
    doTest();
  }

  // PY-48009
  public void testAlternativesAlignmentInOrPatterns() {
    doTest();
  }

  // PY-48009
  public void testAlternativesAlignmentInOrPatternsInsideSequenceLikePattern() {
    doTest();
  }

  // PY-48009
  public void testSpacesAroundEqualSignsInKeywordPatterns() {
    getPythonCodeStyleSettings().SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT = true;
    doTest();
  }

  // PY-52930
  public void testSpaceAfterStarredExcept() {
    doTest();
  }

  // PY-42200
  public void testParenthesizedWithItems() {
    doTest();
  }

  // PY-42200
  public void testHangingClosingBracketInParenthesizedWithItems() {
    getPythonCodeStyleSettings().HANG_CLOSING_BRACKETS = true;
    doTest();
  }

  // PY-42200
  public void testParenthesizedWithItemsHangingIndentProcessedSimilarlyToCollectionsInStatementHeaders() {
    doTest();
  }

  // PY-42200
  public void testParenthesizedWithItemsWrapping() {
    getCodeStyleSettings().setRightMargin(PythonLanguage.getInstance(), 20);
    doTest();
  }

  // PY-28496
  public void testHangingIndentsInMultilineCallChainInParenthesis() {
    doTest();
  }

  // PY-27660
  public void testHangingIndentsInMultilineCallChainInSquareBrackets() {
    doTest();
  }

  public void testMultiLineCallChainSplitByBackslashes() {
    doTest();
  }

  // PY-24792
  public void testNoAlignmentForMultilineBinaryExpressionInReturnStatement() {
    doTest();
  }

  // PY-24792
  public void testNoAlignmentForMultilineBinaryExpressionInYieldStatement() {
    doTest();
  }

  // PY-24792
  public void testNoAlignmentForPartlyParenthesizedMultiLineReturnStatement() {
    doTest();
  }

  // PY-24792
  public void testNoAlignmentForSplitByBackslashesTupleInReturnStatement() {
    doTest();
  }

  // PY-24792
  public void testNoAlignmentForSplitByBackslashesTupleInAssignmentStatement() {
    doTest();
  }

  // PY-24792
  public void testNoAlignmentForSplitByBackslashesTupleInYieldStatement() {
    doTest();
  }

  // PY-61854
  public void testSpaceAfterTypeKeywordInTypeAliasStatement() {
    doTest();
  }

  // PY-61854
  public void testSpaceAfterCommaInTypeParameterList() {
    doTest();
  }

  // PY-61854
  public void testAlignmentInMultilineTypeParameterListInTypeAliasStatement() {
    doTest();
  }

  // PY-61854
  public void testAlignmentInMultilineTypeParameterListInFunctionDefinition() {
    doTest();
  }

  // PY-61854
  public void testAlignmentInMultilineTypeParameterListInClassDefinition() {
    doTest();
  }

  // PY-77060
  public void testSpaceAfterStarInTypeParameterList() {
    doTest();
  }
}
