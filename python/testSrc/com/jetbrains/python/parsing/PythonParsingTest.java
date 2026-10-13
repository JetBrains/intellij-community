// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing;

import com.intellij.idea.TestFor;
import com.jetbrains.python.allure.Subsystems;
import com.jetbrains.python.allure.Layers;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.python.community.helpersLocator.PythonHelpersLocator;
import com.intellij.testFramework.ParsingTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.jetbrains.python.PythonParserDefinition;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;


@TestDataPath("$CONTENT_ROOT/../testData/psi/")
@Subsystems.Parsing
@Layers.Functional
@TestApplication
public class PythonParsingTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  protected final TestFixture<ParsingTestFixture> parsingFixture =
    parsingFixture(projectFixture, PythonHelpersLocator.getPythonCommunityPath() + "/testData/psi", "py", false, createParserDefinition());

  private LanguageLevel myLanguageLevel = LanguageLevel.getDefault();

  protected PythonParserDefinition createParserDefinition() {
    return new PythonParserDefinition();
  }

  @Test
  public void testHelloWorld() {
    doTest();
  }

  @Test
  public void testIfStatement() {
    doTest();
  }

  @Test
  public void testConditionalExpression() {
    doTest();
  }

  @Test
  public void testSubscribedAssignmentLHS() {
    doTest();
  }

  @Test
  public void testConditionalParenLambda() {
    doTest();
  }

  @Test
  public void testLambdaComprehension() {
    doTest();
  }

  @Test
  public void testLambdaConditional() {
    doTest();
  }

  @Test
  public void testTryExceptFinally() {
    doTest();
  }

  @Test
  public void testTryExceptAs() {   // PY-293
    doTest();
  }

  // PY-52930
  @Test
  public void testTryExceptStarNoExpression() {
    doTest();
  }

  // PY-84077
  @Test
  public void testTryExceptMultipleNoParensPre314() {
    doTest(LanguageLevel.PYTHON26);
  }

  // PY-84077
  @Test
  public void testTryExceptMultipleNoParensPost314() {
    doTest(LanguageLevel.PYTHON314);
  }

  // PY-84077
  @Test
  public void testTryExceptMultipleWithParens() {
    doTest();
  }

  @Test
  public void testTryFinally() {
    doTest();
  }

  @Test
  public void testYieldStatement() {
    doTest();
  }

  @Test
  public void testYieldInAssignment() {
    doTest();
  }

  @Test
  public void testYieldInAugAssignment() {
    doTest();
  }

  @Test
  public void testYieldInParentheses() {
    doTest();
  }

  public void _testYieldAsArgument() {
    // this is a strange case: PEP 342 says this syntax is valid, but
    // Python 2.5 doesn't accept it. let's stick with Python behavior for now
    doTest();
  }

  @Test
  public void testDecoratedFunction() {
    doTest();
  }

  @Test
  public void testPrintAsFunction26() {
    doTest(LanguageLevel.PYTHON26);
  }

  @Test
  public void testClassDecorators() {
    doTest(LanguageLevel.PYTHON26);
  }

  @Test
  public void testEmptySuperclassList() {  // PY-321
    doTest();
  }

  @Test
  public void testListComprehensionNestedIf() {  // PY-322
    doTest();
  }

  @Test
  public void testKeywordOnlyArgument() {   // PEP 3102
    doTest(LanguageLevel.PYTHON34);
  }

  @Test
  public void testPy3KKeywords() {
    doTest(LanguageLevel.PYTHON34);
  }

  @Test
  public void testExecPy2() {
    doTest();
  }

  @Test
  public void testExecPy3() {
    doTest(LanguageLevel.PYTHON34);
  }

  @Test
  public void testSuperclassKeywordArguments() {
    doTest(LanguageLevel.PYTHON34);
  }

  @Test
  public void testDictLiteral() {
    doTest();
  }

  @Test
  public void testSetLiteral() {
    doTest(LanguageLevel.PYTHON34);
  }

  @Test
  public void testSetComprehension() {
    doTest(LanguageLevel.PYTHON34);
  }

  @Test
  public void testDictComprehension() {
    doTest(LanguageLevel.PYTHON34);
  }

  @Test
  public void testRaiseFrom() {
    doTest(LanguageLevel.PYTHON34);
  }

  @Test
  public void testEllipsis() {
    doTest();
  }

  @Test
  public void testTupleArguments() {
    doTest();
  }

  @Test
  public void testDefaultTupleArguments() {
    doTest();
  }

  @Test
  public void testExtendedSlices() {
    doTest();
  }

  @Test
  public void testAnnotations() {
    doTest(LanguageLevel.PYTHON34);
  }

  @Test
  public void testNonlocal() {
    doTest(LanguageLevel.PYTHON34);
  }

  @Test
  public void testFloorDiv() {
    doTest();
  }

  @Test
  public void testLongString() {
    doTest();
  }

  @Test
  public void testTrailingSemicolon() {  // PY-363
    doTest();
  }

  @Test
  public void testStarExpression() {   // PEP-3132
    doTest(LanguageLevel.PYTHON34);
  }

  @Test
  public void testDictMissingComma() {  // PY-1025
    doTest();
  }

  @Test
  public void testInconsistentDedent() { // PY-1131
    doTest();
  }

  @Test
  public void testReturnAtEOF() {  // PY-1739
    doTest();
  }

  @Test
  public void testMissingListSeparators() {  // PY-1933
    doTest();
  }

  @Test
  public void testTrailingCommaInList() {
    doTest();
  }

  @Test
  public void testCommentBeforeMethod() { // PY-2209 & friends
    doTest();
  }

  @Test
  public void testBadDecoratorNotMethod() {
    doTest();
  }

  @Test
  public void testCommentAtEndOfMethod() { // PY-2137
    doTest();
  }

  @Test
  public void testCommentAtBeginningOfStatementList() {  // PY-2108
    doTest();
  }

  @Test
  public void testCommentBetweenClasses() {  // PY-1598
    doTest();
  }

  @Test
  public void testIncompleteDict() {
    doTest();
  }

  @Test
  public void testSliceList() {  // PY-1928
    doTest();
  }

  @Test
  public void testDictMissingValue() {  // PY-2791
    doTest();
  }

  @Test
  public void testColonBeforeEof() {  // PY-2790
    doTest();
  }

  @Test
  public void testGeneratorInArgumentList() {  // PY-3172
    doTest();
  }

  @Test
  public void testNestedGenerators() {  // PY-3030
    doTest();
  }

  @Test
  public void testMissingDefaultValue() {  // PY-3253
    doTest();
  }

  @Test
  public void testErrorInParameterList() {  // PY-3635
    doTest();
  }

  @Test
  public void testKeywordAsDefaultParameterValue() {  // PY-3713
    doTest();
  }

  @Test
  public void testTrailingCommaInArgList() {  // PY-4016
    doTest();
  }

  @Test
  public void testMissingParenInCall() {  // PY-4053
    doTest();
  }

  @Test
  public void testTupleAsDictKey() {  // PY-4144
    doTest();
  }

  @Test
  public void testIncompleteStatementList() {  // PY-3792
    doTest();
  }

  @Test
  public void testIncompleteFor() {  // PY-3792
    doTest();
  }

  @Test
  public void testCallInAssignment() {  // PY-5062
    doTest();
  }

  @Test
  public void testCommaAfterStarArg() {  // PY-4039
    doTest();
  }

  // PY-24389
  @Test
  public void testCommaAfterStarArgAllowedInPython36() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testRangeAsLHS() {  // PY-6468
    doTest();
  }

  // PY-6702
  @Test
  public void testYieldFrom() {
    doTest(LanguageLevel.PYTHON34);
  }

  // PY-6733
  @Test
  public void testYieldFromNoExpr() {
    doTest(LanguageLevel.PYTHON34);
  }

  // PY-6734
  @Test
  public void testRaiseFromNoExpr() {
    doTest(LanguageLevel.PYTHON34);
  }

  // PY-6781
  @Test
  public void testComprehensionErrors() {
    doTest();
  }

  // PY-6926
  @Test
  public void testGeneratorList() {
    doTest();
  }

  // EA-30244
  @Test
  public void testEqYieldEq() {
    doTest();
  }

  @Test
  public void testCompoundStatementAfterSemicolon() {  // PY-7660
    doTest();
  }

  // PY-8606
  @Test
  public void testEllipsisInSliceList() {
    doTest();
  }

  // PY-8606
  @Test
  public void testEllipsisInSliceListTail() {
    doTest();
  }

  @Test
  public void testEmptySubscription() {  // PY-8652
    doTest();
  }

  // PY-8752
  @Test
  public void testEllipsisPython3() {
    doTest(LanguageLevel.PYTHON34);
  }

  // PY-8948
  @Test
  public void testNotClosedSlice() {
    doTest();
  }

  // PY-11058
  @Test
  public void testResetAfterSemicolon() {
    doTest();
  }

  @Test
  public void testLoneStar() {  // PY-10177
    doTest();
  }

  @Test
  public void testCommentAfterDecorator() {  // PY-5912
    doTest();
  }

  @Test
  public void testKeywordAsNamedParameter() {  // PY-8318
    doTest();
  }

  @Test
  public void testKeywordAsClassName() {  // PY-8319
    doTest();
  }

  @Test
  public void testKeywordAsFunctionName() {  // PY-8319
    doTest();
  }

  @Test
  public void testIfInList() {  // PY-9561
    doTest();
  }

  @Test
  public void testOverIndentedComment() {  // PY-1909
    doTest();
  }

  @Test
  public void testNotClosedBraceDict() {
    doTest();
  }

  @Test
  public void testNotClosedBraceSet() {
    doTest(LanguageLevel.PYTHON34);
  }

  @Test
  public void testEmptyBlockInFunctionBeforeFunction() {
    doTest();
  }

  @Test
  public void testBlockWithoutColon() {
    doTest();
  }

  @Test
  public void testSingleDefBeforeFunction() {
    doTest();
  }

  @Test
  public void testSingleClassBeforeFunction() {
    doTest();
  }

  // PY-14408
  @Test
  public void testTabInsideContinuationIndent() {
    doTest();
  }

  // PY-15390
  @Test
  public void testMatMul() {
    doTest(LanguageLevel.PYTHON35);
  }

  // PY-15653
  @Test
  public void testMissingFunctionNameAndThenParametersList() {
    doTest();
  }

  // PY-15653
  @Test
  public void testMissingClassNameAndThenListOfBaseClasses() {
    doTest();
  }

  // PY-15653
  @Test
  public void testMissingClassNameAndThenColon() {
    doTest();
  }

  @Test
  public void testAsyncDef() {
    doTest(LanguageLevel.PYTHON35);
  }

  @Test
  public void testAsyncWith() {
    doTest(LanguageLevel.PYTHON35);
  }

  @Test
  public void testAsyncFor() {
    doTest(LanguageLevel.PYTHON35);
  }

  @Test
  public void testAwait() {
    doTest(LanguageLevel.PYTHON35);
  }

  @Test
  public void testDecoratedAsyncDef() {
    doTest(LanguageLevel.PYTHON35);
  }

  @Test
  public void testFStrings() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testEmptyFStrings() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringWithSimpleFragment() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringGluedWithLiteralStringNodes() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringEscapedBraces() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringExpressionContainBraces() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringFragmentWithLiteralFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringFragmentWithInterpolatedFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringFragmentWithNotParenthesizedLambda() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringFragmentWithParenthesizedLambda() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringFragmentWithDictLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringFragmentTypeConversion() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringFragmentIllegalTypeConversion() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringFragmentIncompleteTypeConversionBeforeColon() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringFragmentIncompleteTypeConversionBeforeClosingBrace() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringFragmentIncompleteTypeConversionBeforeClosingQuote() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringFragmentTypeConversionAfterFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringFragmentDuplicateTypeConversion() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringIncompleteFragment() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringIncompleteFragmentWithTypeConversion() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringIncompleteFragmentWithFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringIncompleteFragmentWithTypeConversionAndFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByQuoteOfStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByQuoteInsideStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByQuoteOfNestedStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByQuoteInsideNestedStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByQuoteOfFStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByQuoteInsideFStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByQuoteOfNestedFStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByQuoteInsideNestedFStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByQuoteOfStringLiteralInFormatPart() {
    doTest(LanguageLevel.getLatest());
  }

  @Test
  public void testFStringNotTerminatedByQuoteInsideStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByQuoteOfNestedStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByQuoteInsideNestedStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByQuoteOfFStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByQuoteInsideFStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByQuoteOfNestedFStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByQuoteInsideNestedFStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringTerminatedByQuoteInLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByQuoteInNestedLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringTerminatedByQuoteInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByQuoteInNestedFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringTerminatedByLineBreakInLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringTerminatedByLineBreakInNestedLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringTerminatedByLineBreakInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringTerminatedByLineBreakInNestedFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByLineBreakInExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByLineBreakInNestedExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByLineBreakInExpressionInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByLineBreakInNestedExpressionInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByLineBreakInStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  // not possible to come up with such case without escaping: triple-quoted string inside
  // two nested f-strings with different types of quotes
  @Test
  public void testFStringTerminatedByEscapedLineBreakInNestedStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByLineBreakInStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringTerminatedByLineBreakInNestedStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testMultilineFStringNotTerminatedByQuotesOfStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNotTerminatedByQuotesInsideParenthesizedExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testMultilineFStringContainingMultilineExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testMultilineFStringContainingMultilineExpressionAfterStatementBreak() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testNestedMultilineFStringsWithMultilineExpressions() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testSingleQuotedFStringInsideMultilineFStringTerminatedByLineBreakInText() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testSingleQuotedFStringInsideMultilineFStringNotTerminatedByLineBreakInExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testSingleQuotedFStringInsideMultilineFStringNotTerminatedByLineBreakInExpressionInParentheses() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringAllKindsNested() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringBackslashInsteadOfExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringBackslashAfterExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringBackslashBeforeExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringBackslashInsideMultilineExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testSingleLineFStringContainsCommentInsideExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testSingleLineFStringContainsCommentInsteadOfExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringContainsHashSignInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringContainsHashInLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testUnterminatedFStringWithTrailingBackslashInLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testUnterminatedFStringWithTrailingBackslashInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testMultilineFStringContainsCommentInsideExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testMultilineFStringContainsCommentInsteadOfExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNamedUnicodeEscapeInLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringIncompleteNamedUnicodePrecedingFragment() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNamedUnicodeEscapeInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringNamedUnicodeEscapeInStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringEscapeSequenceInLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringEscapeSequenceInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringEscapedSlashBeforeClosingQuoteInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringSingleSlashBeforeLeftBraceInLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringSingleSlashesBeforeBracesInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringEscapedLineBreakInLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringDeeplyNestedEmptyFragments() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringTrailingWhitespaceInIncompleteFragmentInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-32123
  @Test
  public void testFStringRawFStringInsidePlainFString() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-32123
  @Test
  public void testFStringPlainInsideRawFString() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-32123
  @Test
  public void testFStringEscapeInFormatPartOfRawLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-32123
  @Test
  public void testFStringEscapeInFormatPartOfPlainLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFStringTextTokenMerging() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testIncompleteFStringFragmentRecoveryStoppedAtStatementOnlyKeyword() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-63393
  @Test
  public void testCompleteFStringFragmentTerminatedAtStatementOnlyKeyword() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testNestedIncompleteFStringFragmentRecoveryStoppedAtStatementOnlyKeyword() {
    doTest(LanguageLevel.PYTHON36);
  }

  @Test
  public void testFormatPartFStringFragmentRecoveryStoppedAtStatementOnlyKeyword() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-19036
  @Test
  public void testAwaitInNonAsyncNestedFunction() {
    doTest(LanguageLevel.PYTHON35);
  }

  @Test
  public void testUnpackingExpressions() {
    doTest(LanguageLevel.PYTHON35);
  }

  @Test
  public void testVariableAnnotations() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-64304 EA-247016 
  @Test
  public void testVariableAnnotationRecoveryAwaitExpressionAsTarget() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-20770
  @Test
  public void testAsyncComprehensions() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-20770
  @Test
  public void testAwaitInComprehensions() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-20682
  @Test
  public void testAwaitOnTheSameLineAsFor() {
    doTest(LanguageLevel.PYTHON35);
  }

  // PY-17017
  @Test
  public void testTrailingBlockCommentsAtEndOfFile() {
    doTest();
  }

  // PY-17017
  @Test
  public void testTrailingBlockCommentsFollowedByStatement() {
    doTest();
  }

  // PY-35512
  @Test
  public void testPositionalOnlyParameters() {
    doTest(LanguageLevel.PYTHON38);
  }

  // PY-36009
  @Test
  public void testFStringEqualitySign() {
    doTest(LanguageLevel.PYTHON37);
  }

  // PY-33886
  @Test
  public void testAssignmentExpressions() {
    doTest(LanguageLevel.PYTHON38);
  }

  // PY-33886
  @Test
  public void testInvalidAssignmentExpressions() {
    doTest(LanguageLevel.PYTHON38);
  }

  // PY-33886, PY-36478
  @Test
  public void testInvalidNonParenthesizedAssignmentExpressions() {
    doTest(LanguageLevel.getLatest());
  }

  // PY-33886
  @Test
  public void testAssignmentExpressionsInFString() {
    doTest(LanguageLevel.PYTHON38);
  }

  // PY-36167
  @Test
  public void testFunctionWithPassAndAwaitAfterInPy36() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-41305
  @Test
  public void testExpressionsInDecorators() {
    doTest(LanguageLevel.getLatest());
  }

  @Test
  public void testPatternMatchingMatchAndCaseKeywordsFollowedByNamesakeIdentifiers() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingMatchLooksLikeBinaryExpression() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingMatchLooksLikeCallWithMultipleArguments() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingMatchLooksLikeCallWithSingleArgument() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingMatchLooksLikeCallWithoutArguments() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingMatchLooksLikeIndexing() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingLiteralPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryFStringsInLiteralPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryIllegalNumericExpressionsInLiteralPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryNoSubjectAfterMatch() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryNoPatternAfterCase() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryNoPatternAfterCaseInIntermediateCaseClause() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryIllegalStatementsInsideMatch() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryDanglingBracketsInNestedPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryMatchStatementWithoutClauses() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryMatchStatementWithoutClausesWithComment() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryMatchStatementWithoutClausesAtEndOfFile() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryMatchStatementWithoutClausesWithCommentAtEndOfFile() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingSingleCapturePattern() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingSingleValuePattern() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingValuePatternStartingWithUnderscore() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryIncompleteValuePattern() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingSingleWildcardPattern() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingGroupAndParenthesizedSequencePatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryIncompleteGroupAndParenthesizedSequencePatterns() {
    // XXX Missing statement breaks after "pass" here are odd
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingSequencePatternsInBrackets() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryIncompleteSequencePatternsInBrackets() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoverySequencePatternsMissingCommas() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoverySequenceAndGroupPatternsFollowedByIllegalContent() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryIllegalExpressionInSequencePatternItem() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingStarPatternsInSequences() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryStarPatternMissingIdentifier() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryStarPatternFollowedByQualifiedReference() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingMatchStatementFollowedByAnotherStatement() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryExtraCommasInSequencePatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingMappingPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryIncompleteMappingPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryExtraCommasInMappingPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryDoubleStarWildcardPattern() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingClassPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryIncompleteClassPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryExtraCommasInClassPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryClassPatternsFollowedByIllegalContent() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingOrPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryIncompleteOrPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryExtraBarsInOrPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingAsPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryIncompleteAsPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryAsPatternAsOrPatternComponent() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryAsPatternsWithIllegalTarget() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingTopLevelSequencePatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryExtraCommasInTopLevelSequencePatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingCaseGuards() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingRecoveryIncompleteCaseGuards() {
    doTest(LanguageLevel.PYTHON310);
  }

  @Test
  public void testPatternMatchingLeadingAndTrailingComments() {
    doTest(LanguageLevel.PYTHON310);
  }

  // PY-49990
  @Test
  public void testPatternMatchingVariableTypeDeclarationLooksLikeIncompleteMatchStatement() {
    doTest(LanguageLevel.PYTHON310);
  }

  // PY-49990
  @Test
  public void testPatternMatchingAnnotatedAssignmentLooksLikeIncompleteMatchStatement() {
    doTest(LanguageLevel.PYTHON310);
  }

  // PY-49990
  @Test
  public void testPatternMatchingRecoveryMatchWithColonParsedAsVariableTypeDeclaration() {
    doTest(LanguageLevel.PYTHON310);
  }

  // PY-48940
  @Test
  public void testAssignmentExpressionsInSet() {
    doTest(LanguageLevel.getLatest());
  }

  // PY-48940
  @Test
  public void testAssignmentExpressionsInIndexes() {
    doTest(LanguageLevel.getLatest());
  }

  // PY-42200
  @Test
  public void testWithStatementParenthesizedWithItems() {
    doTest(LanguageLevel.getLatest());
  }

  // PY-43505
  @Test
  public void testWithStatementMultipleWithItemsWithoutParentheses() {
    doTest(LanguageLevel.getLatest());
  }

  // PY-42200
  @Test
  public void testWithStatementWithItemsOwnParentheses() {
    doTest(LanguageLevel.getLatest());
  }

  // PY-42200
  @Test
  public void testWithStatementContextExpressionStartsWithParenthesis() {
    doTest(LanguageLevel.getLatest());
  }

  @Test
  public void testWithStatementRecoveryDanglingComma() {
    doTest(LanguageLevel.getLatest());
  }

  @Test
  public void testWithStatementRecoveryIncompleteParentheses() {
    doTest(LanguageLevel.getLatest());
  }

  @Test
  public void testWithStatementRecoveryMissingColon() {
    doTest(LanguageLevel.getLatest());
  }

  @Test
  public void testWithStatementRecoveryEmptyParentheses() {
    doTest(LanguageLevel.getLatest());
  }

  // PY-9853
  @Test
  public void testWithStatementRecoveryMissingAsName() {
    doTest(LanguageLevel.getLatest());
  }

  @Test
  public void testWithStatementRecoveryNoWithItems() {
    doTest(LanguageLevel.getLatest());
  }

  @Test
  public void testTypeAliasStatementWithoutTypeParameterList() {
    doTest(LanguageLevel.PYTHON312);
  }

  @Test
  public void testTypeAliasStatementWithTypeParameterList() {
    doTest(LanguageLevel.PYTHON312);
  }

  @Test
  public void testTypeAliasStatementWithTypeVarTuple() {
    doTest(LanguageLevel.PYTHON312);
  }

  @Test
  public void testTypeAliasStatementWithParamSpec() {
    doTest(LanguageLevel.PYTHON312);
  }

  @Test
  public void testTypeAliasStatementWithBoundedTypeParameter() {
    doTest(LanguageLevel.PYTHON312);
  }

  @Test
  public void testTypeAliasStatementWithTypeParameterBoundedWithExpression() {
    doTest(LanguageLevel.PYTHON312);
  }

  @Test
  public void testTypeAliasStatementWithTypeParameterAndDanglingComma() {
    // Valid case
    doTest(LanguageLevel.PYTHON312);
  }

  @Test
  public void testTypeAliasStatementWithBoundedTypeParameterAndDanglingComma() {
    // Valid case
    doTest(LanguageLevel.PYTHON312);
  }

  @Test
  public void testTypeAliasStatementRecoveryWithEmptyTypeParameterList() {
    doTest(LanguageLevel.PYTHON312);
  }

  @Test
  public void testTypeAliasStatementRecoveryWithNoAssignedType() {
    doTest(LanguageLevel.PYTHON312);
  }

  @Test
  public void testTypeAliasStatementRecoveryWithEqSignButNoAssignedType() {
    doTest(LanguageLevel.PYTHON312);
  }

  @Test
  public void testTypeParameterInTypeAliasStatementRecoveryIncompleteBound() {
    doTest(LanguageLevel.PYTHON312);
  }

  @Test
  public void testTypeParameterListInTypeAliasStatementRecoveryNotClosedRightBracket() {
    doTest(LanguageLevel.PYTHON312);
  }

  // PY-71002
  @Test
  public void testTypeParameterListInTypeAliasStatementRecoveryNotClosedRightBracketAfterDefault() {
    doTest(LanguageLevel.PYTHON313);
  }

  @Test
  public void testTypeParameterListInTypeAliasStatementRecoveryUnexpectedSymbolAfterComma() {
    doTest(LanguageLevel.PYTHON312);
  }

  @Test
  public void testTypeParameterListInFunctionDeclaration() {
    doTest(LanguageLevel.PYTHON312);
  }

  @Test
  public void testTypeParameterListInFunctionDeclarationRecoveryNotClosedRightBracket() {
    doTest(LanguageLevel.PYTHON312);
  }

  @Test
  public void testTypeParameterListInClassDeclaration() {
    doTest(LanguageLevel.PYTHON312);
  }

  // PY-71002
  @Test
  public void testTypeVarTypeParameterDefaultInClassDeclaration() {
    doTest(LanguageLevel.PYTHON313);
  }

  // PY-71002
  @Test
  public void testTypeVarTypeParameterDefaultInTypeAliasStatement() {
    doTest(LanguageLevel.PYTHON313);
  }

  // PY-71002
  @Test
  public void testParamSpecTypeParameterDefaultInClassDeclaration() {
    doTest(LanguageLevel.PYTHON313);
  }

  // PY-71002
  @Test
  public void testTypeVarTupleTypeParameterDefaultInClassDeclaration() {
    doTest(LanguageLevel.PYTHON313);
  }

  // PY-71002
  @Test
  public void testTypeVarTypeParameterWithDefaultAndBoundInClassDeclaration() {
    doTest(LanguageLevel.PYTHON313);
  }

  // PY-71002
  @Test
  public void testTypeVarTypeParameterDefaultMissingExpression() {
    doTest(LanguageLevel.PYTHON313);
  }

  // PY-71002
  @Test
  public void testTypeVarTypeParameterWithBoundAndDefaultMissingExpression() {
    doTest(LanguageLevel.PYTHON313);
  }

  // PY-74231
  @Test
  public void testTypeAliasStatementInClassBody() {
    doTest(LanguageLevel.PYTHON312);
  }

  // PY-74231
  @Test
  public void testTypeAliasStatementInFunctionBody() {
    doTest(LanguageLevel.PYTHON312);
  }

  // PY-74321
  @Test
  public void testTypeAliasStatementInsideStatementListContainers() {
    doTest(LanguageLevel.PYTHON312);
  }

  @Test
  public void testTypeKeywordAsIdentifier() {
    doTest(LanguageLevel.PYTHON312);
  }

  // PY-79967
  @Test
  public void testTemplateStrings() {
    doTest(LanguageLevel.PYTHON314);
  }

  // PY-79967
  @Test
  public void testTemplateStringWithFragment() {
    doTest(LanguageLevel.PYTHON314);
  }

  // PY-79967
  @Test
  public void testEmptyTemplateStrings() {
    doTest(LanguageLevel.PYTHON314);
  }

  // PY-79967
  @Test
  public void testNestedTemplateStrings() {
    doTest(LanguageLevel.PYTHON314);
  }

  // PY-79967
  @Test
  public void testTemplateStringInsideFString() {
    doTest(LanguageLevel.PYTHON314);
  }

  // PY-79967
  @Test
  public void testFStringInsideTemplateString() {
    doTest(LanguageLevel.PYTHON314);
  }

  // PY-88664, PEP 810
  @Test
  public void testLazyImport() {
    doTest(LanguageLevel.PYTHON315);
  }

  // PY-88664, PEP 810: `lazy` outside import context stays an identifier
  @Test
  public void testLazyImportNotAKeyword() {
    doTest(LanguageLevel.PYTHON315);
  }

  // PY-88664, PEP 810: `lazy import …` is still parsed as a lazy import on Python < 3.15
  // so that users get a single, accurate compatibility error rather than the misleading
  // `Unresolved reference 'lazy'` suggestion to install the `lazy` package.
  @Test
  public void testLazyImportOnOlderPython() {
    doTest(LanguageLevel.PYTHON314);
  }

  // PEP 798: unpacking (* and **) in comprehensions and generator expressions
  @Test
  public void testUnpackingInComprehensions() {
    doTest(LanguageLevel.PYTHON315);
  }

  @Test
  @TestFor(issues = "PY-78251")
  public void testCommentIsTheOnlyLineOfBlock() {
    // The test data files lose the last line break. This input needs it.
    doCodeTest("if a:\n    # comment\n");
  }

  @Test
  @TestFor(issues = "PY-78251")
  public void testCommentIsTheOnlyLineOfBlockAtEndOfFile() {
    doTest();
  }

  @Test
  @TestFor(issues = "PY-78251")
  public void testOverIndentedCommentBeforeBlockBody() {
    doTest();
  }

  @Test
  @TestFor(issues = "PY-78251")
  public void testCommentSeparatedByBlankLineFromBlock() {
    doTest();
  }

  @Test
  @TestFor(issues = "PY-78251")
  public void testCommentOnlyBlockFollowedByDedentedStatement() {
    doTest();
  }

  @Test
  @TestFor(issues = "PY-78251")
  public void testCommentOnlyFunctionFollowedByFunction() {
    doTest();
  }

  @Test
  @TestFor(issues = "PY-78251")
  public void testCommentOnlyMethodFollowedByTopLevelStatement() {
    doTest();
  }

  public void doTest() {
    doTest(LanguageLevel.PYTHON26);
  }

  public void doTest(LanguageLevel languageLevel) {
    ParsingTestFixture fixture = parsingFixture.get();
    LanguageLevel prev = myLanguageLevel;
    myLanguageLevel = languageLevel;
    try {
      String name = fixture.getTestName();
      String text = fixture.loadFile(name + "." + fixture.getFileExt());
      PsiFile file = fixture.checkParsedFile(createFile(name + "." + fixture.getFileExt(), text), text);
      fixture.checkResult(name, file);
    }
    finally {
      myLanguageLevel = prev;
    }
    PsiFile file = fixture.getFile();
    ReadAction.runBlocking(() -> ensureEachFunctionHasStatementList(file, PyFunction.class));
  }

  private void doCodeTest(@NotNull String code) {
    ParsingTestFixture fixture = parsingFixture.get();
    PsiFile file = createFile("a." + fixture.getFileExt(), code);
    fixture.setFile(file);
    ReadAction.runBlocking(() -> {
      ParsingTestUtil.ensureParsed(file);
      assertEquals(code, file.getText());
    });
    fixture.checkResult(fixture.getFilePrefix() + fixture.getTestName(), file);
  }

  private PsiFile createFile(@NotNull String name, @NotNull String text) {
    final PsiFile file = parsingFixture.get().createFile(name, text);
    PythonLanguageLevelPusher.specifyFileLanguageLevel(file.getVirtualFile(), myLanguageLevel);
    return file;
  }

  public static <T extends PyFunction> void ensureEachFunctionHasStatementList(
    @NotNull PsiFile parentFile,
    @NotNull Class<T> functionType) {
    Collection<T> functions = PsiTreeUtil.findChildrenOfType(parentFile, functionType);
    for (T functionToCheck : functions) {
      functionToCheck.getStatementList(); //To make sure each function has statement list (does not throw exception)
    }
  }
}
