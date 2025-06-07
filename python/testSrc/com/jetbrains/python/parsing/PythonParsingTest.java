// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.parsing;

import com.intellij.lang.LanguageASTFactory;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.*;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.impl.PyPsiFacadeImpl;
import com.jetbrains.python.psi.impl.PythonASTFactory;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


@TestDataPath("$CONTENT_ROOT/../testData/psi/")
public class PythonParsingTest extends ParsingTestCase {
  private LanguageLevel myLanguageLevel = LanguageLevel.getDefault();

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public PythonParsingTest() {
    this(new PythonParserDefinition());
  }

  protected PythonParsingTest(PythonParserDefinition parserDefinition) {
    super("psi", "py", parserDefinition);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Registry.markAsLoaded();
    registerExtensionPoint(PythonDialectsTokenSetContributor.EP_NAME, PythonDialectsTokenSetContributor.class);
    registerExtension(PythonDialectsTokenSetContributor.EP_NAME, new PythonTokenSetContributor());
    addExplicitExtension(LanguageASTFactory.INSTANCE, PythonLanguage.getInstance(), new PythonASTFactory());
    getProject().registerService(PyPsiFacade.class, PyPsiFacadeImpl.class);
    getApplication().registerService(PyElementTypesFacade.class, PyElementTypesFacadeImpl.class);
    getApplication().registerService(PyLanguageFacade.class, PyLanguageFacadeImpl.class);
  }

  @Override
  protected String getTestDataPath() {
    return PythonHelpersLocator.getPythonCommunityPath() + "/testData";
  }

  public void testHelloWorld() {
    doTest();
  }

  public void testIfStatement() {
    doTest();
  }

  public void testConditionalExpression() {
    doTest();
  }

  public void testSubscribedAssignmentLHS() {
    doTest();
  }

  public void testConditionalParenLambda() {
    doTest();
  }

  public void testLambdaComprehension() {
    doTest();
  }

  public void testLambdaConditional() {
    doTest();
  }

  public void testTryExceptFinally() {
    doTest();
  }

  public void testTryFinally() {
    doTest();
  }

  public void testYieldStatement() {
    doTest();
  }

  public void testYieldInAssignment() {
    doTest();
  }

  public void testYieldInAugAssignment() {
    doTest();
  }

  public void testYieldInParentheses() {
    doTest();
  }

  public void _testYieldAsArgument() {
    // this is a strange case: PEP 342 says this syntax is valid, but
    // Python 2.5 doesn't accept it. let's stick with Python behavior for now
    doTest();
  }

  public void testDecoratedFunction() {
    doTest();
  }

  public void testTryExceptAs() {   // PY-293
    doTest();
  }

  // PY-52930
  public void testTryExceptStarNoExpression() {
    doTest();
  }

  public void testPrintAsFunction26() {
    doTest(LanguageLevel.PYTHON26);
  }

  public void testClassDecorators() {
    doTest(LanguageLevel.PYTHON26);
  }

  public void testEmptySuperclassList() {  // PY-321
    doTest();
  }

  public void testListComprehensionNestedIf() {  // PY-322
    doTest();
  }

  public void testKeywordOnlyArgument() {   // PEP 3102
    doTest(LanguageLevel.PYTHON34);
  }

  public void testPy3KKeywords() {
    doTest(LanguageLevel.PYTHON34);
  }

  public void testExecPy2() {
    doTest();
  }

  public void testExecPy3() {
    doTest(LanguageLevel.PYTHON34);
  }

  public void testSuperclassKeywordArguments() {
    doTest(LanguageLevel.PYTHON34);
  }

  public void testDictLiteral() {
    doTest();
  }

  public void testSetLiteral() {
    doTest(LanguageLevel.PYTHON34);
  }

  public void testSetComprehension() {
    doTest(LanguageLevel.PYTHON34);
  }

  public void testDictComprehension() {
    doTest(LanguageLevel.PYTHON34);
  }

  public void testRaiseFrom() {
    doTest(LanguageLevel.PYTHON34);
  }

  public void testEllipsis() {
    doTest();
  }

  public void testTupleArguments() {
    doTest();
  }

  public void testDefaultTupleArguments() {
    doTest();
  }

  public void testExtendedSlices() {
    doTest();
  }

  public void testAnnotations() {
    doTest(LanguageLevel.PYTHON34);
  }

  public void testNonlocal() {
    doTest(LanguageLevel.PYTHON34);
  }

  public void testFloorDiv() {
    doTest();
  }

  public void testLongString() {
    doTest();
  }

  public void testTrailingSemicolon() {  // PY-363
    doTest();
  }

  public void testStarExpression() {   // PEP-3132
    doTest(LanguageLevel.PYTHON34);
  }

  public void testDictMissingComma() {  // PY-1025
    doTest();
  }

  public void testInconsistentDedent() { // PY-1131
    doTest();
  }

  public void testReturnAtEOF() {  // PY-1739
    doTest();
  }

  public void testMissingListSeparators() {  // PY-1933
    doTest();
  }

  public void testTrailingCommaInList() {
    doTest();
  }

  public void testCommentBeforeMethod() { // PY-2209 & friends
    doTest();
  }

  public void testBadDecoratorNotMethod() {
    doTest();
  }

  public void testCommentAtEndOfMethod() { // PY-2137
    doTest();
  }

  public void testCommentAtBeginningOfStatementList() {  // PY-2108
    doTest();
  }

  public void testCommentBetweenClasses() {  // PY-1598
    doTest();
  }

  public void testIncompleteDict() {
    doTest();
  }

  public void testSliceList() {  // PY-1928
    doTest();
  }

  public void testDictMissingValue() {  // PY-2791
    doTest();
  }

  public void testColonBeforeEof() {  // PY-2790
    doTest();
  }

  public void testGeneratorInArgumentList() {  // PY-3172
    doTest();
  }

  public void testNestedGenerators() {  // PY-3030
    doTest();
  }

  public void testMissingDefaultValue() {  // PY-3253
    doTest();
  }

  public void testErrorInParameterList() {  // PY-3635
    doTest();
  }

  public void testKeywordAsDefaultParameterValue() {  // PY-3713
    doTest();
  }

  public void testTrailingCommaInArgList() {  // PY-4016
    doTest();
  }

  public void testMissingParenInCall() {  // PY-4053
    doTest();
  }

  public void testTupleAsDictKey() {  // PY-4144
    doTest();
  }

  public void testIncompleteStatementList() {  // PY-3792
    doTest();
  }

  public void testIncompleteFor() {  // PY-3792
    doTest();
  }

  public void testCallInAssignment() {  // PY-5062
    doTest();
  }

  public void testCommaAfterStarArg() {  // PY-4039
    doTest();
  }

  // PY-24389
  public void testCommaAfterStarArgAllowedInPython36() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testRangeAsLHS() {  // PY-6468
    doTest();
  }

  // PY-6702
  public void testYieldFrom() {
    doTest(LanguageLevel.PYTHON34);
  }

  // PY-6733
  public void testYieldFromNoExpr() {
    doTest(LanguageLevel.PYTHON34);
  }

  // PY-6734
  public void testRaiseFromNoExpr() {
    doTest(LanguageLevel.PYTHON34);
  }

  // PY-6781
  public void testComprehensionErrors() {
    doTest();
  }

  // PY-6926
  public void testGeneratorList() {
    doTest();
  }

  // EA-30244
  public void testEqYieldEq() {
    doTest();
  }

  public void testCompoundStatementAfterSemicolon() {  // PY-7660
    doTest();
  }

  // PY-8606
  public void testEllipsisInSliceList() {
    doTest();
  }

  // PY-8606
  public void testEllipsisInSliceListTail() {
    doTest();
  }

  public void testEmptySubscription() {  // PY-8652
    doTest();
  }

  // PY-8752
  public void testEllipsisPython3() {
    doTest(LanguageLevel.PYTHON34);
  }

  // PY-8948
  public void testNotClosedSlice() {
    doTest();
  }

  // PY-11058
  public void testResetAfterSemicolon() {
    doTest();
  }

  public void testLoneStar() {  // PY-10177
    doTest();
  }

  public void testCommentAfterDecorator() {  // PY-5912
    doTest();
  }

  public void testKeywordAsNamedParameter() {  // PY-8318
    doTest();
  }

  public void testKeywordAsClassName() {  // PY-8319
    doTest();
  }

  public void testKeywordAsFunctionName() {  // PY-8319
    doTest();
  }

  public void testIfInList() {  // PY-9561
    doTest();
  }

  public void testOverIndentedComment() {  // PY-1909
    doTest();
  }

  public void testNotClosedBraceDict() {
    doTest();
  }

  public void testNotClosedBraceSet() {
    doTest(LanguageLevel.PYTHON34);
  }

  public void testEmptyBlockInFunctionBeforeFunction() {
    doTest();
  }

  public void testBlockWithoutColon() {
    doTest();
  }

  public void testSingleDefBeforeFunction() {
    doTest();
  }

  public void testSingleClassBeforeFunction() {
    doTest();
  }

  // PY-14408
  public void testTabInsideContinuationIndent() {
    doTest();
  }

  // PY-15390
  public void testMatMul() {
    doTest(LanguageLevel.PYTHON35);
  }

  // PY-15653
  public void testMissingFunctionNameAndThenParametersList() {
    doTest();
  }

  // PY-15653
  public void testMissingClassNameAndThenListOfBaseClasses() {
    doTest();
  }

  // PY-15653
  public void testMissingClassNameAndThenColon() {
    doTest();
  }

  public void testAsyncDef() {
    doTest(LanguageLevel.PYTHON35);
  }

  public void testAsyncWith() {
    doTest(LanguageLevel.PYTHON35);
  }

  public void testAsyncFor() {
    doTest(LanguageLevel.PYTHON35);
  }

  public void testAwait() {
    doTest(LanguageLevel.PYTHON35);
  }

  public void testDecoratedAsyncDef() {
    doTest(LanguageLevel.PYTHON35);
  }

  public void testFStrings() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testEmptyFStrings() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringWithSimpleFragment() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringGluedWithLiteralStringNodes() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringEscapedBraces() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringExpressionContainBraces() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringFragmentWithLiteralFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringFragmentWithInterpolatedFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringFragmentWithNotParenthesizedLambda() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringFragmentWithParenthesizedLambda() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringFragmentWithDictLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringFragmentTypeConversion() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringFragmentIllegalTypeConversion() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringFragmentIncompleteTypeConversionBeforeColon() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringFragmentIncompleteTypeConversionBeforeClosingBrace() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringFragmentIncompleteTypeConversionBeforeClosingQuote() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringFragmentTypeConversionAfterFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringFragmentDuplicateTypeConversion() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringIncompleteFragment() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringIncompleteFragmentWithTypeConversion() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringIncompleteFragmentWithFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringIncompleteFragmentWithTypeConversionAndFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByQuoteOfStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByQuoteInsideStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByQuoteOfNestedStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByQuoteInsideNestedStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByQuoteOfFStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByQuoteInsideFStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByQuoteOfNestedFStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByQuoteInsideNestedFStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByQuoteOfStringLiteralInFormatPart() {
    doTest(LanguageLevel.getLatest());
  }

  public void testFStringNotTerminatedByQuoteInsideStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByQuoteOfNestedStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByQuoteInsideNestedStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByQuoteOfFStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByQuoteInsideFStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByQuoteOfNestedFStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByQuoteInsideNestedFStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteInLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByQuoteInNestedLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByQuoteInNestedFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByLineBreakInLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByLineBreakInNestedLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByLineBreakInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByLineBreakInNestedFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByLineBreakInExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByLineBreakInNestedExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByLineBreakInExpressionInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByLineBreakInNestedExpressionInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByLineBreakInStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  // not possible to come up with such case without escaping: triple-quoted string inside
  // two nested f-strings with different types of quotes
  public void testFStringTerminatedByEscapedLineBreakInNestedStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByLineBreakInStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByLineBreakInNestedStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testMultilineFStringNotTerminatedByQuotesOfStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNotTerminatedByQuotesInsideParenthesizedExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testMultilineFStringContainingMultilineExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testMultilineFStringContainingMultilineExpressionAfterStatementBreak() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testNestedMultilineFStringsWithMultilineExpressions() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testSingleQuotedFStringInsideMultilineFStringTerminatedByLineBreakInText() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testSingleQuotedFStringInsideMultilineFStringNotTerminatedByLineBreakInExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testSingleQuotedFStringInsideMultilineFStringNotTerminatedByLineBreakInExpressionInParentheses() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringAllKindsNested() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringBackslashInsteadOfExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringBackslashAfterExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringBackslashBeforeExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringBackslashInsideMultilineExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testSingleLineFStringContainsCommentInsideExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testSingleLineFStringContainsCommentInsteadOfExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringContainsHashSignInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringContainsHashInLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testUnterminatedFStringWithTrailingBackslashInLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testUnterminatedFStringWithTrailingBackslashInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testMultilineFStringContainsCommentInsideExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testMultilineFStringContainsCommentInsteadOfExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNamedUnicodeEscapeInLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringIncompleteNamedUnicodePrecedingFragment() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNamedUnicodeEscapeInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringNamedUnicodeEscapeInStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringEscapeSequenceInLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringEscapeSequenceInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringEscapedSlashBeforeClosingQuoteInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringSingleSlashBeforeLeftBraceInLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringSingleSlashesBeforeBracesInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringEscapedLineBreakInLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringDeeplyNestedEmptyFragments() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTrailingWhitespaceInIncompleteFragmentInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-32123
  public void testFStringRawFStringInsidePlainFString() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-32123
  public void testFStringPlainInsideRawFString() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-32123
  public void testFStringEscapeInFormatPartOfRawLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-32123
  public void testFStringEscapeInFormatPartOfPlainLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTextTokenMerging() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testIncompleteFStringFragmentRecoveryStoppedAtStatementOnlyKeyword() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-63393
  public void testCompleteFStringFragmentTerminatedAtStatementOnlyKeyword() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testNestedIncompleteFStringFragmentRecoveryStoppedAtStatementOnlyKeyword() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFormatPartFStringFragmentRecoveryStoppedAtStatementOnlyKeyword() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-19036
  public void testAwaitInNonAsyncNestedFunction() {
    doTest(LanguageLevel.PYTHON35);
  }

  public void testUnpackingExpressions() {
    doTest(LanguageLevel.PYTHON35);
  }

  public void testVariableAnnotations() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-64304 EA-247016 
  public void testVariableAnnotationRecoveryAwaitExpressionAsTarget() {
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

  // PY-20682
  public void testAwaitOnTheSameLineAsFor() {
    doTest(LanguageLevel.PYTHON35);
  }

  // PY-17017
  public void testTrailingBlockCommentsAtEndOfFile() {
    doTest();
  }

  // PY-17017
  public void testTrailingBlockCommentsFollowedByStatement() {
    doTest();
  }

  // PY-35512
  public void testPositionalOnlyParameters() {
    doTest(LanguageLevel.PYTHON38);
  }

  // PY-36009
  public void testFStringEqualitySign() {
    doTest(LanguageLevel.PYTHON37);
  }

  // PY-33886
  public void testAssignmentExpressions() {
    doTest(LanguageLevel.PYTHON38);
  }

  // PY-33886
  public void testInvalidAssignmentExpressions() {
    doTest(LanguageLevel.PYTHON38);
  }

  // PY-33886, PY-36478
  public void testInvalidNonParenthesizedAssignmentExpressions() {
    doTest(LanguageLevel.getLatest());
  }

  // PY-33886
  public void testAssignmentExpressionsInFString() {
    doTest(LanguageLevel.PYTHON38);
  }

  // PY-36167
  public void testFunctionWithPassAndAwaitAfterInPy36() {
    doTest(LanguageLevel.PYTHON36);
  }

  // PY-41305
  public void testExpressionsInDecorators() {
    doTest(LanguageLevel.getLatest());
  }

  public void testPatternMatchingMatchAndCaseKeywordsFollowedByNamesakeIdentifiers() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingMatchLooksLikeBinaryExpression() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingMatchLooksLikeCallWithMultipleArguments() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingMatchLooksLikeCallWithSingleArgument() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingMatchLooksLikeCallWithoutArguments() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingMatchLooksLikeIndexing() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingLiteralPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryFStringsInLiteralPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryIllegalNumericExpressionsInLiteralPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryNoSubjectAfterMatch() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryNoPatternAfterCase() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryNoPatternAfterCaseInIntermediateCaseClause() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryIllegalStatementsInsideMatch() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryDanglingBracketsInNestedPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryMatchStatementWithoutClauses() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryMatchStatementWithoutClausesWithComment() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryMatchStatementWithoutClausesAtEndOfFile() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryMatchStatementWithoutClausesWithCommentAtEndOfFile() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingSingleCapturePattern() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingSingleValuePattern() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingValuePatternStartingWithUnderscore() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryIncompleteValuePattern() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingSingleWildcardPattern() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingGroupAndParenthesizedSequencePatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryIncompleteGroupAndParenthesizedSequencePatterns() {
    // XXX Missing statement breaks after "pass" here are odd
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingSequencePatternsInBrackets() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryIncompleteSequencePatternsInBrackets() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoverySequencePatternsMissingCommas() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoverySequenceAndGroupPatternsFollowedByIllegalContent() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryIllegalExpressionInSequencePatternItem() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingStarPatternsInSequences() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryStarPatternMissingIdentifier() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryStarPatternFollowedByQualifiedReference() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingMatchStatementFollowedByAnotherStatement() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryExtraCommasInSequencePatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingMappingPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryIncompleteMappingPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryExtraCommasInMappingPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryDoubleStarWildcardPattern() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingClassPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryIncompleteClassPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryExtraCommasInClassPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryClassPatternsFollowedByIllegalContent() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingOrPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryIncompleteOrPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryExtraBarsInOrPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingAsPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryIncompleteAsPatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryAsPatternAsOrPatternComponent() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryAsPatternsWithIllegalTarget() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingTopLevelSequencePatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryExtraCommasInTopLevelSequencePatterns() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingCaseGuards() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingRecoveryIncompleteCaseGuards() {
    doTest(LanguageLevel.PYTHON310);
  }

  public void testPatternMatchingLeadingAndTrailingComments() {
    doTest(LanguageLevel.PYTHON310);
  }

  // PY-49990
  public void testPatternMatchingVariableTypeDeclarationLooksLikeIncompleteMatchStatement() {
    doTest(LanguageLevel.PYTHON310);
  }

  // PY-49990
  public void testPatternMatchingAnnotatedAssignmentLooksLikeIncompleteMatchStatement() {
    doTest(LanguageLevel.PYTHON310);
  }

  // PY-49990
  public void testPatternMatchingRecoveryMatchWithColonParsedAsVariableTypeDeclaration() {
    doTest(LanguageLevel.PYTHON310);
  }

  // PY-48940
  public void testAssignmentExpressionsInSet() {
    doTest(LanguageLevel.getLatest());
  }

  // PY-48940
  public void testAssignmentExpressionsInIndexes() {
    doTest(LanguageLevel.getLatest());
  }

  // PY-42200
  public void testWithStatementParenthesizedWithItems() {
    doTest(LanguageLevel.getLatest());
  }

  // PY-43505
  public void testWithStatementMultipleWithItemsWithoutParentheses() {
    doTest(LanguageLevel.getLatest());
  }

  // PY-42200
  public void testWithStatementWithItemsOwnParentheses() {
    doTest(LanguageLevel.getLatest());
  }

  // PY-42200
  public void testWithStatementContextExpressionStartsWithParenthesis() {
    doTest(LanguageLevel.getLatest());
  }

  public void testWithStatementRecoveryDanglingComma() {
    doTest(LanguageLevel.getLatest());
  }

  public void testWithStatementRecoveryIncompleteParentheses() {
    doTest(LanguageLevel.getLatest());
  }

  public void testWithStatementRecoveryMissingColon() {
    doTest(LanguageLevel.getLatest());
  }

  public void testWithStatementRecoveryEmptyParentheses() {
    doTest(LanguageLevel.getLatest());
  }

  // PY-9853
  public void testWithStatementRecoveryMissingAsName() {
    doTest(LanguageLevel.getLatest());
  }

  public void testWithStatementRecoveryNoWithItems() {
    doTest(LanguageLevel.getLatest());
  }

  public void testTypeAliasStatementWithoutTypeParameterList() {
    doTest(LanguageLevel.PYTHON312);
  }

  public void testTypeAliasStatementWithTypeParameterList() {
    doTest(LanguageLevel.PYTHON312);
  }

  public void testTypeAliasStatementWithTypeVarTuple() {
    doTest(LanguageLevel.PYTHON312);
  }

  public void testTypeAliasStatementWithParamSpec() {
    doTest(LanguageLevel.PYTHON312);
  }

  public void testTypeAliasStatementWithBoundedTypeParameter() {
    doTest(LanguageLevel.PYTHON312);
  }

  public void testTypeAliasStatementWithTypeParameterBoundedWithExpression() {
    doTest(LanguageLevel.PYTHON312);
  }

  public void testTypeAliasStatementWithTypeParameterAndDanglingComma() {
    // Valid case
    doTest(LanguageLevel.PYTHON312);
  }

  public void testTypeAliasStatementWithBoundedTypeParameterAndDanglingComma() {
    // Valid case
    doTest(LanguageLevel.PYTHON312);
  }

  public void testTypeAliasStatementRecoveryWithEmptyTypeParameterList() {
    doTest(LanguageLevel.PYTHON312);
  }

  public void testTypeAliasStatementRecoveryWithNoAssignedType() {
    doTest(LanguageLevel.PYTHON312);
  }

  public void testTypeAliasStatementRecoveryWithEqSignButNoAssignedType() {
    doTest(LanguageLevel.PYTHON312);
  }

  public void testTypeParameterInTypeAliasStatementRecoveryIncompleteBound() {
    doTest(LanguageLevel.PYTHON312);
  }

  public void testTypeParameterListInTypeAliasStatementRecoveryNotClosedRightBracket() {
    doTest(LanguageLevel.PYTHON312);
  }

  // PY-71002
  public void testTypeParameterListInTypeAliasStatementRecoveryNotClosedRightBracketAfterDefault() {
    doTest(LanguageLevel.PYTHON313);
  }

  public void testTypeParameterListInTypeAliasStatementRecoveryUnexpectedSymbolAfterComma() {
    doTest(LanguageLevel.PYTHON312);
  }

  public void testTypeParameterListInFunctionDeclaration() {
    doTest(LanguageLevel.PYTHON312);
  }

  public void testTypeParameterListInFunctionDeclarationRecoveryNotClosedRightBracket() {
    doTest(LanguageLevel.PYTHON312);
  }

  public void testTypeParameterListInClassDeclaration() {
    doTest(LanguageLevel.PYTHON312);
  }

  // PY-71002
  public void testTypeVarTypeParameterDefaultInClassDeclaration() {
    doTest(LanguageLevel.PYTHON313);
  }

  // PY-71002
  public void testTypeVarTypeParameterDefaultInTypeAliasStatement() {
    doTest(LanguageLevel.PYTHON313);
  }

  // PY-71002
  public void testParamSpecTypeParameterDefaultInClassDeclaration() {
    doTest(LanguageLevel.PYTHON313);
  }

  // PY-71002
  public void testTypeVarTupleTypeParameterDefaultInClassDeclaration() {
    doTest(LanguageLevel.PYTHON313);
  }

  // PY-71002
  public void testTypeVarTypeParameterWithDefaultAndBoundInClassDeclaration() {
    doTest(LanguageLevel.PYTHON313);
  }

  // PY-71002
  public void testTypeVarTypeParameterDefaultMissingExpression() {
    doTest(LanguageLevel.PYTHON313);
  }

  // PY-71002
  public void testTypeVarTypeParameterWithBoundAndDefaultMissingExpression() {
    doTest(LanguageLevel.PYTHON313);
  }

  // PY-74231
  public void testTypeAliasStatementInClassBody() {
    doTest(LanguageLevel.PYTHON312);
  }

  // PY-74231
  public void testTypeAliasStatementInFunctionBody() {
    doTest(LanguageLevel.PYTHON312);
  }

  // PY-74321
  public void testTypeAliasStatementInsideStatementListContainers() {
    doTest(LanguageLevel.PYTHON312);
  }

  public void testTypeKeywordAsIdentifier() {
    doTest(LanguageLevel.PYTHON312);
  }

  // PY-79967
  public void testTemplateStrings() {
    doTest(LanguageLevel.PYTHON314);
  }

  // PY-79967
  public void testTemplateStringWithFragment() {
    doTest(LanguageLevel.PYTHON314);
  }

  // PY-79967
  public void testEmptyTemplateStrings() {
    doTest(LanguageLevel.PYTHON314);
  }

  // PY-79967
  public void testNestedTemplateStrings() {
    doTest(LanguageLevel.PYTHON314);
  }

  // PY-79967
  public void testTemplateStringInsideFString() {
    doTest(LanguageLevel.PYTHON314);
  }

  // PY-79967
  public void testFStringInsideTemplateString() {
    doTest(LanguageLevel.PYTHON314);
  }

  public void doTest() {
    doTest(LanguageLevel.PYTHON26);
  }

  public void doTest(LanguageLevel languageLevel) {
    LanguageLevel prev = myLanguageLevel;
    myLanguageLevel = languageLevel;
    try {
      doTest(true);
    }
    finally {
      myLanguageLevel = prev;
    }
    ensureEachFunctionHasStatementList(myFile, PyFunction.class);
  }

  @Override
  protected PsiFile createFile(@NotNull String name, @NotNull String text) {
    final PsiFile file = super.createFile(name, text);
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
