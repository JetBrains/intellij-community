// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.intentions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.ui.TestInputDialog;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Alexey.Ivanov
 */
public class PyIntentionTest extends PyTestCase {
  @Nullable private PyDocumentationSettings myDocumentationSettings = null;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDocumentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    myDocumentationSettings.setFormat(DocStringFormat.REST);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      TestDialogManager.setTestInputDialog(TestInputDialog.DEFAULT);
      if (myDocumentationSettings != null) {
        myDocumentationSettings.setFormat(DocStringFormat.PLAIN);
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private void doTest(String hint) {
    doTest(hint, false);
  }

  private void doTest(String hint, boolean ignoreWhiteSpaces) {
    final PsiFile file = myFixture.configureByFile("intentions/" + getTestName(true) + ".py");
    final IntentionAction action = myFixture.findSingleIntention(hint);
    assertSdkRootsNotParsed(file);
    myFixture.launchAction(action);
    myFixture.checkResultByFile("intentions/" + getTestName(true) + "_after.py", ignoreWhiteSpaces);
  }

  private void doMultiFileTest(@NotNull String hint) {
    final String directoryPath = "intentions/" + getTestName(false);
    final String filesPathPrefix = directoryPath + "/" + getTestName(true);
    myFixture.copyDirectoryToProject(directoryPath, "");
    myFixture.configureByFile(filesPathPrefix + ".py");
    final IntentionAction action = myFixture.findSingleIntention(hint);
    myFixture.launchAction(action);
    myFixture.checkResultByFile(filesPathPrefix + ".py", filesPathPrefix + "_after.py", false);
  }

  /**
   * Ensures that intention with given hint <i>is not</i> active.
   *
   * @param hint
   */
  private void doNegativeTest(@NotNull String hint) {
    final PsiFile file = myFixture.configureByFile("intentions/" + getTestName(true) + ".py");
    List<IntentionAction> ints = myFixture.filterAvailableIntentions(hint);
    assertEmpty("Intention '" + hint + "' should not be available under caret", ints);
    assertSdkRootsNotParsed(file);
  }

  public void testReplaceExceptPart() {
    doTest(PyPsiBundle.message("INTN.convert.except.to"));
  }

  public void testConvertBuiltins() {
    doTest(PyPsiBundle.message("INTN.convert.builtin.import"));
  }

  public void testRemoveLeadingF() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> doTest(PyPsiBundle.message("QFIX.remove.string.prefix", "F")));
  }

  // PY-18972
  public void testRemoveTrailingL() {
    doTest(PyPsiBundle.message("QFIX.remove.trailing.suffix"));
  }

  public void testReplaceOctalNumericLiteral() {
    doTest(PyPsiBundle.message("INTN.replace.octal.numeric.literal"));
  }

  public void testReplaceListComprehensions() {
    doTest(PyPsiBundle.message("INTN.replace.list.comprehensions"));
  }

  public void testReplaceRaiseStatement() {
    doTest(PyPsiBundle.message("INTN.replace.raise.statement"));
  }

  public void testReplaceBackQuoteExpression() {
    doTest(PyPsiBundle.message("INTN.replace.backquote.expression"));
  }

  public void testSplitIf() {
    doTest(PyPsiBundle.message("INTN.split.if"));
  }

  public void testNegateComparison() {
    doTest(PyPsiBundle.message("INTN.negate.comparison", "<=", ">"));
  }

  public void testNegateComparison2() {
    doTest(PyPsiBundle.message("INTN.negate.comparison", ">", "<="));
  }

  public void testFlipComparison() {
    doTest(PyPsiBundle.message("INTN.flip.comparison.to.operator", ">", "<"));
  }

  public void testReplaceListComprehensionWithFor() {
    doTest(PyPsiBundle.message("INTN.replace.list.comprehensions.with.for"));
  }

  public void testReplaceListComprehension2() {    //PY-6731
    doTest(PyPsiBundle.message("INTN.replace.list.comprehensions.with.for"));
  }

  public void testJoinIf() {
    doTest(PyPsiBundle.message("INTN.join.if"));
  }

  public void testJoinIfElse() {
    doNegativeTest(PyPsiBundle.message("INTN.join.if"));
  }

  public void testJoinIfBinary() {              //PY-4697
    doTest(PyPsiBundle.message("INTN.join.if"));
  }

  public void testJoinIfMultiStatements() {           //PY-2970
    doNegativeTest(PyPsiBundle.message("INTN.join.if"));
  }

  public void testJoinIfOrExpressionInOuterCondition() {
    doTest(PyPsiBundle.message("INTN.join.if"));
  }

  // EA-401551
  public void testJoinIfAssignmentExpressionInInnerCondition() {
    doTest(PyPsiBundle.message("INTN.join.if"));
  }

  public void testJoinIfAssignmentExpressionsInBothConditions() {
    doTest(PyPsiBundle.message("INTN.join.if"));
  }

  public void testDictConstructorToLiteralForm() {
    doTest(PyPsiBundle.message("INTN.convert.dict.constructor.to.dict.literal"));
  }

  public void testDictLiteralFormToConstructor() {
    doTest(PyPsiBundle.message("INTN.convert.dict.literal.to.dict.constructor"));
  }

  public void testDictLiteralFormToConstructor1() {
    doNegativeTest(PyPsiBundle.message("INTN.convert.dict.literal.to.dict.constructor"));
  }

  public void testDictLiteralFormToConstructor2() {      //PY-5157
    doNegativeTest(PyPsiBundle.message("INTN.convert.dict.literal.to.dict.constructor"));
  }

  public void testDictLiteralFormToConstructor3() {
    doNegativeTest(PyPsiBundle.message("INTN.convert.dict.literal.to.dict.constructor"));
  }

  public void testQuotedString() {      //PY-2915
    doTest(PyPsiBundle.message("INTN.quoted.string.double.to.single"));
  }

  public void testQuotedStringDoubleSlash() {      //PY-3295
    doTest(PyPsiBundle.message("INTN.quoted.string.single.to.double"));
  }

  public void testEscapedQuotedString() { //PY-2656
    doTest(PyPsiBundle.message("INTN.quoted.string.single.to.double"));
  }

  public void testDoubledQuotedString() { //PY-2689
    doTest(PyPsiBundle.message("INTN.quoted.string.double.to.single"));
  }

  public void testMultilineQuotedString() { //PY-8064
    getIndentOptions().INDENT_SIZE = 2;
    doTest(PyPsiBundle.message("INTN.quoted.string.double.to.single"));
  }

  // PY-15608
  public void testConvertingQuotesOfGluedStringWithDifferentElementQuotes() {
    doTest(PyPsiBundle.message("INTN.quoted.string.single.to.double"));
  }

  // PY-30798
  public void testConvertingFStringQuotesNotSuggestedInsideInnerExpressions() {
    doNegativeTest(PyPsiBundle.message("INTN.quoted.string.single.to.double"));
  }

  // PY-30798
  public void testConvertingFStringQuotesSuggestedOnFragmentBraces() {
    doTest(PyPsiBundle.message("INTN.quoted.string.single.to.double"));
  }

  // PY-30798
  public void testConvertingFStringQuotesSuggestedOnFragmentFormatPart() {
    doTest(PyPsiBundle.message("INTN.quoted.string.single.to.double"));
  }

  // PY-30798
  public void testConvertingRawFStringQuotes() {
    doTest(PyPsiBundle.message("INTN.quoted.string.single.to.double"));
  }

  // PY-30798
  public void testConvertingQuotesNotSuggestedForStringInsideFStringWithOppositeQuotes() {
    doNegativeTest(PyPsiBundle.message("INTN.quoted.string.single.to.double"));
  }

  // PY-30798
  public void testConvertingQuotesNotSuggestedForStringInsideFStringThatWouldRequireEscapingInsideFragment() {
    doNegativeTest(PyPsiBundle.message("INTN.quoted.string.single.to.double"));
  }

  // PY-30798
  public void testConvertingQuotesNotSuggestedForFStringContainingStringWithInconvertibleQuotes() {
    doNegativeTest(PyPsiBundle.message("INTN.quoted.string.single.to.double"));
  }

  // PY-30798
  public void testConvertingQuotesOfStringInsideFString() {
    doTest(PyPsiBundle.message("INTN.quoted.string.double.to.single"));
  }

  // PY-30798
  public void testConvertingQuotesOfFStringContainingOtherStrings() {
    doTest(PyPsiBundle.message("INTN.quoted.string.double.to.single"));
  }

  // PY-30798
  public void testConvertingQuotesOfFStringContainingEscapedQuotes() {
    doTest(PyPsiBundle.message("INTN.quoted.string.single.to.double"));
  }

  // PY-30798
  public void testConvertingQuotesOfGluedFStringContainingOtherStrings() {
    doTest(PyPsiBundle.message("INTN.quoted.string.single.to.double"));
  }

  // PY-38315
  public void testConvertingQuotesOfEmptyString() {
    doTest(PyPsiBundle.message("INTN.quoted.string.single.to.double"));
  }

  public void testConvertLambdaToFunction() {
    doTest(PyPsiBundle.message("INTN.convert.lambda.to.function"));
  }

  public void testConvertLambdaToFunction1() {    //PY-6610
    doNegativeTest(PyPsiBundle.message("INTN.convert.lambda.to.function"));
  }

  public void testConvertLambdaToFunction2() {    //PY-6835
    doTest(PyPsiBundle.message("INTN.convert.lambda.to.function"));
  }

  public void testConvertVariadicParam() { //PY-2264
    doTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  // PY-20254
  public void testConvertVariadicParamEmptySubscription() {
    doNegativeTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  // PY-25035
  public void testConvertVariadicParamNoUsages() {
    doNegativeTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  // PY-25035
  public void testConvertVariadicParamUnrelatedCaret() {
    doNegativeTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  public void testConvertVariadicParamInvalidIdentifiers() {
    doTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  public void testConvertVariadicParamPositionalContainerInPy2() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> doNegativeTest(PyPsiBundle.message("INTN.convert.variadic.param")));
  }

  public void testConvertVariadicParamPositionalContainerInPy3() {
    doTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  // PY-26284
  public void testConvertVariadicParamKeywordContainerPop() {
    doTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  // PY-26285
  public void testConvertVariadicParamOverriddenInNested() {
    doTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  // PY-26285
  public void testConvertVariadicParamNotOverriddenInNested() {
    doTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  // PY-2264
  public void testConvertVariadicParamKwargsReused() {
    doNegativeTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  // PY-2264
  public void testConvertVariadicParamUnpackedKwargsReused() {
    doNegativeTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  // PY-26286
  public void testConvertVariadicParamSeveralSubscriptions() {
    doTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  // PY-26286
  public void testConvertVariadicParamSeveralCalls() {
    doTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  // PY-26286
  public void testConvertVariadicParamSeveralCallsWithSameDefaultValue() {
    doTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  // PY-26286
  public void testConvertVariadicParamSeveralCallsWithDifferentDefaultValue() {
    doNegativeTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  // PY-26286
  public void testConvertVariadicParamSeveralSubscriptionsAndCalls() {
    doTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  // PY-26286
  public void testConvertVariadicParamSeveralSubscriptionsAndCallsWithSameDefaultValue() {
    doTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  // PY-26286
  public void testConvertVariadicParamSeveralSubscriptionsAndCallsWithDifferentDefaultValue() {
    doTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  // PY-26286
  public void testConvertVariadicParamSeveralCallsWithDifferentKeysCaretOnContainer() {
    doTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  // PY-26286
  public void testConvertVariadicParamSeveralCallsWithDifferentKeysCaretOnAvailableKey() {
    doTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  // PY-26286
  public void testConvertVariadicParamSeveralCallsWithDifferentKeysCaretOnUnavailableKey() {
    doNegativeTest(PyPsiBundle.message("INTN.convert.variadic.param"));
  }

  public void testConvertTripleQuotedString() { //PY-2697
    doTest(PyPsiBundle.message("INTN.triple.quoted.string"));
  }

  public void testConvertTripleQuotedString1() { //PY-7774
    doTest(PyPsiBundle.message("INTN.triple.quoted.string"));
  }

  public void testConvertTripleQuotedStringInParenthesized() { //PY-7883
    doTest(PyPsiBundle.message("INTN.triple.quoted.string"));
  }

  public void testConvertTripleQuotedUnicodeString() { //PY-7152
    doTest(PyPsiBundle.message("INTN.triple.quoted.string"));
  }

  public void testConvertTripleQuotedParenthesizedString() { //PY-7151
    doTest(PyPsiBundle.message("INTN.triple.quoted.string"));
  }

  // PY-8989
  public void testConvertTripleQuotedStringRawStrings() {
    doTest(PyPsiBundle.message("INTN.triple.quoted.string"));
  }

  // PY-8989
  public void testConvertTripleQuotedStringDoesNotReplacePythonEscapes() {
    doTest(PyPsiBundle.message("INTN.triple.quoted.string"));
  }

  // PY-8989
  public void testConvertTripleQuotedStringMultilineGluedString() {
    doTest(PyPsiBundle.message("INTN.triple.quoted.string"));
  }

  public void testConvertTripleQuotedEmptyString() {
    doTest(PyPsiBundle.message("INTN.triple.quoted.string"));
  }

  public void testTransformConditionalExpression() { //PY-3094
    doTest(PyPsiBundle.message("INTN.transform.into.if.else.statement"));
  }

  public void testImportFromToImport() {
    doTest("Convert to 'import sys'");
  }

  // PY-11074
  public void testImportToImportFrom() {
    doTest("Convert to 'from builtins import ...'");
  }

  public void testTypeInDocstring() {
    getIndentOptions().INDENT_SIZE = 2;
    doDocParamTypeTest(DocStringFormat.REST);
  }

  public void testTypeInDocstring3() {
    doDocParamTypeTest(DocStringFormat.REST);
  }

  public void testTypeInDocstring4() {
    doDocParamTypeTest(DocStringFormat.REST);
  }

  public void testTypeInDocstringParameterInCallable() {
    doDocParamTypeTest(DocStringFormat.REST);
  }

  public void testTypeInDocstring5() {
    getIndentOptions().INDENT_SIZE = 2;
    doDocParamTypeTest(DocStringFormat.REST);
  }

  public void testTypeInDocstringAtTheEndOfFunction() {
    doDocReturnTypeTest(DocStringFormat.REST);
  }

  public void testTypeInDocstring6() {         //PY-7973
    doNegativeTest(PyPsiBundle.message("INTN.specify.return.type.in.docstring"));
  }

  public void testTypeInDocstring7() {         //PY-8930
    doDocParamTypeTest(DocStringFormat.REST);
  }

  // PY-16456
  public void testTypeInDocStringDifferentIndentationSize() {
    doDocParamTypeTest(DocStringFormat.REST);
  }

  public void testParamTypeInDocstringNotSuggestedForSelf() {
    doNegativeTest(PyPsiBundle.message("INTN.specify.type.in.docstring"));
  }

  // PY-31369
  public void testTypeCommentNotAffectSpecifyTypeInDocstringIntention() {
    doTest(PyPsiBundle.message("INTN.specify.type.in.docstring"));
  }

  public void testParamTypeInAnnotationNotSuggestedForSelf() {
    doNegativeTest(PyPsiBundle.message("INTN.specify.type.in.annotation"));
  }

  public void testParamTypeInDocstringNotSuggestedForLambda() {
    doNegativeTest(PyPsiBundle.message("INTN.specify.type.in.docstring"));
  }

  public void testParamTypeInAnnotationNotSuggestedForLambda() {
    doNegativeTest(PyPsiBundle.message("INTN.specify.type.in.annotation"));
  }

  // PY-16456
  public void testReturnTypeInDocStringDifferentIndentationSize() {
    doDocReturnTypeTest(DocStringFormat.REST);
  }

  public void testReturnTypeInDocstring() {
    doDocReturnTypeTest(DocStringFormat.REST);
  }

  public void testTypeInDocstring1() {
    getIndentOptions().INDENT_SIZE = 2;
    doDocReturnTypeTest(DocStringFormat.REST);
  }

  public void testTypeInDocstring2() {
    getIndentOptions().INDENT_SIZE = 2;
    doDocReturnTypeTest(DocStringFormat.REST);
  }

  public void testTypeInPy3Annotation() {      //PY-7045
    doTypeAnnotationTest();
  }

  public void testReturnTypeInPy3Annotation() {      //PY-7085
    doTest(PyPsiBundle.message("INTN.specify.return.type.in.annotation"));
  }

  public void testReturnTypeInPy3Annotation1() {      //PY-8783
    doTest(PyPsiBundle.message("INTN.specify.return.type.in.annotation"));
  }

  public void testReturnTypeInPy3Annotation2() {      //PY-8783
    doTest(PyPsiBundle.message("INTN.specify.return.type.in.annotation"));
  }

  // PY-17094
  public void testReturnTypeInPy3AnnotationLocalFunction() {
    doTest(PyPsiBundle.message("INTN.specify.return.type.in.annotation"));
  }

  public void testReturnTypeInPy3AnnotationNoColon() {
    doTest(PyPsiBundle.message("INTN.specify.return.type.in.annotation"));
  }

  public void testTypeAnnotation3() {  //PY-7087
    doTypeAnnotationTest();
  }

  private void doTypeAnnotationTest() {
    doTest(PyPsiBundle.message("INTN.specify.type.in.annotation"));
  }

  public void testTypeAssertion() {
    doTestTypeAssertion();
  }

  public void testTypeAssertion1() { //PY-7089
    doTestTypeAssertion();
  }

  public void testTypeAssertion2() {
    doTestTypeAssertion();
  }

  public void testTypeAssertion3() {                   //PY-7403
    doNegativeTest(PyPsiBundle.message("INTN.insert.assertion"));
  }

  public void testTypeAssertion4() {  //PY-7971
    doTestTypeAssertion();
  }

  public void testTypeAssertionInDictComp() {  //PY-7971
    doNegativeTest(PyPsiBundle.message("INTN.insert.assertion"));
  }

  private void doTestTypeAssertion() {
    doTest(PyPsiBundle.message("INTN.insert.assertion"));
  }

  public void testDocStub() {
    doDocStubTest(DocStringFormat.REST);
  }

  public void testOneLineDocStub() {
    doDocStubTest(DocStringFormat.REST);
  }

  public void testDocStubKeywordOnly() {
    getIndentOptions().INDENT_SIZE = 2;
    doDocStubTest(DocStringFormat.REST);
  }

  // PY-16765
  public void testGoogleDocStubCustomIndent() {
    getIndentOptions().INDENT_SIZE = 2;
    doDocStubTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testGoogleDocStubInlineFunctionBody() {
    doDocStubTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testGoogleDocStubInlineFunctionBodyMultilineParametersList() {
    doDocStubTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testGoogleDocStubInlineFunctionBodyNoSpaceBefore() {
    doDocStubTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testGoogleDocStubEmptyFunctionBody() {
    doDocStubTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testReturnTypeInNewGoogleDocString() {
    doDocReturnTypeTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testParamTypeInNewGoogleDocString() {
    doDocParamTypeTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testParamTypeInEmptyGoogleDocString() {
    doDocParamTypeTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testParamTypeInGoogleDocStringOnlySummaryOneLine() {
    doDocParamTypeTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testParamTypeInGoogleDocStringOnlySummary() {
    doDocParamTypeTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testParamTypeInGoogleDocStringEmptyParamSection() {
    doDocParamTypeTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testParamTypeInGoogleDocStringParamDeclaredNoParenthesis() {
    doDocParamTypeTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testParamTypeInGoogleDocStringParamDeclaredEmptyParenthesis() {
    doDocParamTypeTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testParamTypeInGoogleDocStringOtherParamDeclared() {
    doDocParamTypeTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testParamTypeInGoogleDocStringOtherSectionExists() {
    doDocParamTypeTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testReturnTypeInEmptyGoogleDocString() {
    doDocReturnTypeTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testReturnTypeInGoogleDocStringEmptyReturnSection() {
    doDocReturnTypeTest(DocStringFormat.GOOGLE);
  }

  // PY-16758
  public void testGoogleReturnSectionAfterKeywords() {
    doDocReturnTypeTest(DocStringFormat.GOOGLE);
  }

  // PY-16758
  public void testGoogleReturnSectionAfterYields() {
    doDocReturnTypeTest(DocStringFormat.GOOGLE);
  }

  // PY-16758
  public void testGoogleReturnSectionBeforeRaises() {
    doDocReturnTypeTest(DocStringFormat.GOOGLE);
  }

  // PY-16758
  public void testParamSectionBeforeKeywords() {
    doDocAddMissingParamsTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testGoogleDocStubWithTypes() {
    final PyCodeInsightSettings codeInsightSettings = PyCodeInsightSettings.getInstance();
    final boolean oldInsertTypeDocStub = codeInsightSettings.INSERT_TYPE_DOCSTUB;
    codeInsightSettings.INSERT_TYPE_DOCSTUB = true;
    try {
      doDocStubTest(DocStringFormat.GOOGLE);
    }
    finally {
      codeInsightSettings.INSERT_TYPE_DOCSTUB = oldInsertTypeDocStub;
    }
  }

  // PY-4717
  public void testNumpyDocStub() {
    doDocStubTest(DocStringFormat.NUMPY);
  }

  // PY-4717
  public void testNumpyDocStubWithTypes() {
    final PyCodeInsightSettings codeInsightSettings = PyCodeInsightSettings.getInstance();
    final boolean oldInsertTypeDocStub = codeInsightSettings.INSERT_TYPE_DOCSTUB;
    codeInsightSettings.INSERT_TYPE_DOCSTUB = true;
    try {
      doDocStubTest(DocStringFormat.NUMPY);
    }
    finally {
      codeInsightSettings.INSERT_TYPE_DOCSTUB = oldInsertTypeDocStub;
    }
  }

  // PY-15332
  public void testGoogleNoReturnSectionForInit() {
    doDocStubTest(DocStringFormat.GOOGLE);
  }

  // PY-15332
  public void testRestNoReturnTagForInit() {
    doDocStubTest(DocStringFormat.REST);
  }

  // PY-16904
  public void testNumpyAddMissingParameterPreservesNoneIndent() {
    doDocAddMissingParamsTest(DocStringFormat.NUMPY);
  }

  // PY-9795
  public void testAddMissingParamsInGoogleDocString() {
    doDocAddMissingParamsTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testAddMissingParamsInGoogleDocStringNoParamSection() {
    doDocAddMissingParamsTest(DocStringFormat.GOOGLE);
  }

  // PY-16765
  public void testAddMissingParamsInGoogleDocStringNoParamSectionCustomCodeIndent() {
    getIndentOptions().INDENT_SIZE = 2;
    doDocAddMissingParamsTest(DocStringFormat.GOOGLE);
  }

  // PY-9795
  public void testAddMissingParamsInGoogleDocStringEmptyParamSection() {
    doDocAddMissingParamsTest(DocStringFormat.GOOGLE);
  }

  // PY-16765
  public void testAddMissingParamsInGoogleDocStringEmptyParamSectionCustomCodeIndent() {
    getIndentOptions().INDENT_SIZE = 2;
    doDocAddMissingParamsTest(DocStringFormat.GOOGLE);
  }

  // PY-4717
  public void testReturnTypeInNewNumpyDocString() {
    doDocReturnTypeTest(DocStringFormat.NUMPY);
  }

  // PY-4717
  public void testParamTypeInNewNumpyDocString() {
    doDocParamTypeTest(DocStringFormat.NUMPY);
  }

   // PY-4717
  public void testParamTypeInEmptyNumpyDocString() {
    doDocParamTypeTest(DocStringFormat.NUMPY);
  }

  // PY-4717
  public void testParamTypeInNumpyDocStringOnlySummaryOneLine() {
    doDocParamTypeTest(DocStringFormat.NUMPY);
  }

  // PY-4717
  public void testParamTypeInNumpyDocStringOnlySummary() {
    doDocParamTypeTest(DocStringFormat.NUMPY);
  }

  // PY-4717
  public void testParamTypeInNumpyDocStringEmptyParamSection() {
    doDocParamTypeTest(DocStringFormat.NUMPY);
  }

  // PY-4717
  public void testParamTypeInNumpyDocStringParamDeclaredNoColon() {
    doDocParamTypeTest(DocStringFormat.NUMPY);
  }

  // PY-4717
  public void testParamTypeInNumpyDocStringParamDeclaredColon() {
    doDocParamTypeTest(DocStringFormat.NUMPY);
  }

  // PY-4717
  public void testParamTypeInNumpyDocStringOtherParamDeclared() {
    doDocParamTypeTest(DocStringFormat.NUMPY);
  }

  // PY-4717
  public void testParamTypeInNumpyDocStringOtherSectionExists() {
    doDocParamTypeTest(DocStringFormat.NUMPY);
  }

  // PY-16908
  public void testParamTypeInNumpyDocStringCombinedParams() {
    doDocParamTypeTest(DocStringFormat.NUMPY);
  }

  // PY-16908
  public void testParamTypeInNumpyDocStringCombinedParamsColon() {
    doDocParamTypeTest(DocStringFormat.NUMPY);
  }

  // PY-4717
  public void testReturnTypeInEmptyNumpyDocString() {
    doDocReturnTypeTest(DocStringFormat.NUMPY);
  }

  // PY-4717
  public void testReturnTypeInNumpyDocStringEmptyReturnSection() {
    doDocReturnTypeTest(DocStringFormat.NUMPY);
  }

  // PY-16761
  public void testPositionalVarargTypeInGoogleDocString() {
    doDocParamTypeTest(DocStringFormat.GOOGLE);
  }

  // PY-16761
  public void testKeywordVarargTypeInGoogleDocString() {
    doDocParamTypeTest(DocStringFormat.GOOGLE);
  }

  // PY-16761
  public void testAddMissingVarargsInGoogleDocString() {
    doDocAddMissingParamsTest(DocStringFormat.GOOGLE);
  }

  // PY-7383
  public void testYieldFrom() {
    doTest(PyPsiBundle.message("INTN.yield.from"));
  }

  public void testConvertStaticMethodToFunction() {
    doTest(PyPsiBundle.message("INTN.convert.static.method.to.function"));
  }

  public void testConvertStaticMethodToFunctionUsage() {
    doTest(PyPsiBundle.message("INTN.convert.static.method.to.function"));
  }

  // PY-24482
  public void testImportToggleAlias() {
    TestDialogManager.setTestInputDialog(new TestInputDialog() {
      @Override
      public String show(String message) {
        return "mc"; //NON-NLS
      }
    });
    doMultiFileTest(PyPsiBundle.message("INTN.add.import.alias.to.name", "MyClass"));
  }

  private void doDocStubTest(@NotNull DocStringFormat format) {
    runWithDocStringFormat(format, () -> {
      CodeInsightSettings.getInstance().JAVADOC_STUB_ON_ENTER = true;
      doTest(PyPsiBundle.message("INTN.insert.docstring.stub"), true);
    });
  }

  private void doDocParamTypeTest(@NotNull DocStringFormat format) {
    runWithDocStringFormat(format, () -> doTest(PyPsiBundle.message("INTN.specify.type.in.docstring")));
  }

  private void doDocReturnTypeTest(@NotNull DocStringFormat format) {
    runWithDocStringFormat(format, () -> doTest(PyPsiBundle.message("INTN.specify.return.type.in.docstring")));

  }

  public void doDocAddMissingParamsTest(@NotNull DocStringFormat format) {
    runWithDocStringFormat(format, () -> doTest(PyPsiBundle.message("INTN.add.parameters.to.docstring")));

  }
}
