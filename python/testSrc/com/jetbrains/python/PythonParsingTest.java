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

import com.intellij.lang.LanguageASTFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PythonASTFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author yole
 */
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
    registerExtensionPoint(PythonDialectsTokenSetContributor.EP_NAME, PythonDialectsTokenSetContributor.class);
    registerExtension(PythonDialectsTokenSetContributor.EP_NAME, new PythonTokenSetContributor());
    addExplicitExtension(LanguageASTFactory.INSTANCE, PythonLanguage.getInstance(), new PythonASTFactory());
    PythonDialectsTokenSetProvider.reset();
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath();
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

  public void testWithStatement() {
    doTest();
  }

  public void testDecoratedFunction() {
    doTest();
  }

  public void testTryExceptAs() {   // PY-293
    doTest();
  }

  public void testWithStatement26() {
    doTest(LanguageLevel.PYTHON26);
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

  public void testWithStatement31() {
    doTest(LanguageLevel.PYTHON34);
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

  public void testWithMissingID() {  // PY-9853
    doTest(LanguageLevel.PYTHON27);
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

  public void testFStringTerminatedByQuoteOfStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteInsideStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteOfNestedStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteInsideNestedStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteOfFStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteInsideFStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteOfNestedFStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteInsideNestedFStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteOfStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteInsideStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteOfNestedStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteInsideNestedStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteOfFStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteInsideFStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteOfNestedFStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteInsideNestedFStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteInLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteInNestedLiteralPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByQuoteInNestedFormatPart() {
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

  public void testFStringTerminatedByLineBreakInExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByLineBreakInNestedExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByLineBreakInExpressionInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByLineBreakInNestedExpressionInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByLineBreakInStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  // not possible to come up with such case without escaping: triple-quoted string inside
  // two nested f-strings with different types of quotes
  public void testFStringTerminatedByEscapedLineBreakInNestedStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByLineBreakInStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testFStringTerminatedByLineBreakInNestedStringLiteralInFormatPart() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testMultilineFStringTerminatedByQuotesOfStringLiteral() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testMultilineFStringTerminatedByQuotesInsideParenthesizedExpression() {
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

  public void testSingleQuotedFStringInsideMultilineFStringTerminatedByLineBreakInExpression() {
    doTest(LanguageLevel.PYTHON36);
  }

  public void testSingleQuotedFStringInsideMultilineFStringTerminatedByLineBreakInExpressionInParentheses() {
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
    file.getVirtualFile().putUserData(LanguageLevel.KEY, myLanguageLevel);
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
