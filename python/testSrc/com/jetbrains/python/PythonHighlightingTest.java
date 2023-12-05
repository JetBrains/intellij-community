// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Test highlighting added by annotators.
 */
public class PythonHighlightingTest extends PyTestCase {

  private EditorColorsScheme myOriginalScheme;

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return ourPy2Descriptor;
  }

  public void testBuiltins() {
    EditorColorsScheme scheme = createTemporaryColorScheme();

    TextAttributesKey xKey;
    TextAttributes xAttributes;

    xKey = TextAttributesKey.find("PY.BUILTIN_NAME");
    xAttributes = new TextAttributes(Color.green, Color.black, Color.white, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    xKey = TextAttributesKey.find("PY.PREDEFINED_USAGE");
    xAttributes = new TextAttributes(Color.yellow, Color.black, Color.white, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    doTest();
  }

  public void testDeclarations() {
    EditorColorsScheme scheme = createTemporaryColorScheme();

    TextAttributesKey xKey = TextAttributesKey.find("PY.CLASS_DEFINITION");
    TextAttributes xAttributes = new TextAttributes(Color.blue, Color.black, Color.white, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    xKey = TextAttributesKey.find("PY.FUNC_DEFINITION");
    xAttributes = new TextAttributes(Color.red, Color.black, Color.white, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    xKey = TextAttributesKey.find("PY.PREDEFINED_DEFINITION");
    xAttributes = new TextAttributes(Color.green, Color.black, Color.white, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    doTest();
  }

  public void testAssignmentTargets() {
    runWithLanguageLevel(LanguageLevel.PYTHON26, () -> doTest(true, false));
  }

  public void testAssignmentTargetWith() {  // PY-7529
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> doTest(true, false));
  }

  public void testAssignmentTargets3K() {
    doTest(LanguageLevel.PYTHON34, true, false);
  }

  public void testBreakOutsideOfLoop() {
    doTest(true, false);
  }

  public void testReturnOutsideOfFunction() {
    doTest();
  }

  public void testContinueOutsideOfLoop() {
    doTest(false, false);
  }

  // PY-36003
  public void testContinueInFinallyBlockBefore38() {
    doTest(LanguageLevel.PYTHON37, false, false);
  }

  // PY-36003
  public void testContinueInFinallyBlock() {
    doTest(LanguageLevel.PYTHON38, false, false);
  }

  public void testReturnWithArgumentsInGenerator() {
    doTest();
  }

  public void testYieldOutsideOfFunction() {
    doTest(LanguageLevel.PYTHON27, true, true);
  }

  public void testYieldInDefaultValue() {
    doTest(LanguageLevel.PYTHON34, true, false);
  }

  // PY-11663
  public void testYieldInLambda() {
    doTest();
  }

  public void testImportStarAtTopLevel() {
    doTest(true, false);
  }

  public void testMalformedStringUnterminated() {
    doTest();
  }

  public void testMalformedStringEscaped() {
    doTest(false, false);
  }

  public void testStringEscapedOK() {
    doTest();
  }

  public void testStringMixedSeparatorsOK() {   // PY-299
    doTest();
  }

  public void testStringBytesLiteralOK() {
    doTest(LanguageLevel.PYTHON26, true, true);
  }

  public void testArgumentList() {
    doTest(true, false);
  }

  public void testRegularAfterVarArgs() {
    doTest(LanguageLevel.PYTHON34, true, false);
  }

  public void testKeywordOnlyArguments() {
    doTest(LanguageLevel.PYTHON34, true, false);
  }

  public void testMalformedStringTripleQuoteUnterminated() {
    doTest();
  }

  public void testMixedTripleQuotes() {   // PY-2806
    doTest();
  }

  public void testOddNumberOfQuotes() {  // PY-2802
    doTest(true, false);
  }

  public void testEscapedBackslash() {  // PY-2994
    doTest(true, false);
  }

  public void testMultipleEscapedBackslashes() {
    doTest(true, false);
  }

  public void testUnsupportedFeaturesInPython3() {
    doTest(LanguageLevel.PYTHON34, true, false);
  }

  // PY-6703
  public void testUnicode33() {
    doTest(LanguageLevel.PYTHON34, true, false);
  }

  public void testParenthesizedGenerator() {
    doTest(false, false);
  }

  public void testStarInGenerator() {  // PY-10177
    doTest(LanguageLevel.PYTHON34, false, false);
  }

  public void testStarArgs() {  // PY-6456
    doTest(LanguageLevel.PYTHON34, true, false);
  }

  public void testDocstring() {  // PY-8025
    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    documentationSettings.setFormat(DocStringFormat.REST);
    try {
      doTest(false, true);
    }
    finally {
      documentationSettings.setFormat(DocStringFormat.PLAIN);
    }
  }

  public void testYieldInNestedFunction() {
    // highlight func declaration first, lest we get an "Extra fragment highlighted" error.
    EditorColorsScheme scheme = createTemporaryColorScheme();

    TextAttributesKey xKey = TextAttributesKey.find("PY.FUNC_DEFINITION");
    TextAttributes xAttributes = new TextAttributes(Color.red, Color.black, Color.white, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    doTest();
  }

  // PY-33235
  public void testNestedFunction() {
    EditorColorsScheme scheme = createTemporaryColorScheme();

    TextAttributesKey xKey = TextAttributesKey.find("PY.CLASS_DEFINITION");
    TextAttributes xAttributes = new TextAttributes(Color.blue, Color.black, Color.white, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    xKey = TextAttributesKey.find("PY.FUNC_DEFINITION");
    xAttributes = new TextAttributes(Color.red, Color.black, Color.white, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    xKey = TextAttributesKey.find("PY.NESTED_FUNC_DEFINITION");
    xAttributes = new TextAttributes(Color.green, Color.blue, Color.white, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    doTest();
  }

  public void testAsync() {
    doTest(LanguageLevel.PYTHON36, true, true);
  }

  public void testAwait() {
    doTest(LanguageLevel.PYTHON36, true, true);
  }

  public void testAwaitInTuple() {
    doTest(LanguageLevel.PYTHON36, true, false);
  }

  public void testAwaitInGenerator() {
    doTest(LanguageLevel.PYTHON36, true, false);
  }
  // PY-20770
  public void testAwaitInListPy36() {
    doTest(LanguageLevel.PYTHON36, true, false);
  }

  // PY-20770
  public void testAwaitInSetPy36() {
    doTest(LanguageLevel.PYTHON36, true, false);
  }

  // PY-20770
  public void testAwaitInDictPy36() {
    doTest(LanguageLevel.PYTHON36, true, false);
  }

  // PY-20770
  public void testYieldInsideAsyncDefPy36() {
    doTest(LanguageLevel.PYTHON36, true, false);
  }

  public void testUnpackingStar() {
    doTest(LanguageLevel.PYTHON36, false, false);
  }

  // PY-52930
  public void testExceptionGroupsStarNoWarning() {
    doTest(LanguageLevel.getLatest(), false, false);
  }

  // PY-52930
  public void testExceptionGroupsStarOlderPythonWarning() {
    doTest(LanguageLevel.PYTHON310, false, false);
  }

  // PY-52930
  public void testExceptionGroupInExceptOk() {
    doTest(LanguageLevel.getLatest(), false, false);
  }

  // PY-52930
  public void testExceptionGroupInExceptStar() {
    doTest(LanguageLevel.getLatest(), false, false);
  }

  // PY-52930
  public void testExceptionGroupInTupleInExceptStar() {
    doTest(LanguageLevel.getLatest(), false, false);
  }

  // PY-52930
  public void testExceptStarAndExceptInTheSameTry() {
    doTest(LanguageLevel.getLatest(), false, false);
  }

  // PY-52930
  public void testContinueBreakReturnInExceptStar() {
    doTest(LanguageLevel.getLatest(), false, false);
  }

  // PY-52930
  public void testContinueBreakInsideLoopInExceptStarPart() {
    doTest(LanguageLevel.getLatest(), false, false);
  }

  // PY-52930
  public void testReturnInsideFunctionInExceptStarPart() {
    doTest(LanguageLevel.getLatest(), false, false);
  }

  // PY-35961
  public void testUnpackingInNonParenthesizedTuplesInReturnAndYieldBefore38() {
    doTest(LanguageLevel.PYTHON36, false, false);
  }

  // PY-35961
  public void testUnpackingInNonParenthesizedTuplesInReturnAndYield() {
    doTest(LanguageLevel.PYTHON38, false, false);
  }

  // PY-19927
  public void testMagicMethods() {
    EditorColorsScheme scheme = createTemporaryColorScheme();

    TextAttributesKey xKey = TextAttributesKey.find("PY.PREDEFINED_DEFINITION");
    TextAttributes xAttributes = new TextAttributes(Color.green, Color.black, Color.white, EffectType.BOXED, Font.BOLD);
    scheme.setAttributes(xKey, xAttributes);

    doTest();
  }

  // PY-19775
  public void testAsyncBuiltinMethods() {
    doTest(LanguageLevel.PYTHON36, true, false);
  }

  // PY-28017
  public void testAsyncModuleBuiltinMethods() {
    doTest(LanguageLevel.PYTHON37, true, false);
  }

  // PY-28017
  public void testModuleBuiltinMethods() {
    doTest(LanguageLevel.PYTHON37, false, true);
  }

  public void testImplicitOctLongInteger() {
    doTest(LanguageLevel.PYTHON36, true, false);
  }

  public void testIllegalVariableAnnotationTarget() {
    doTest(LanguageLevel.PYTHON36, true, false);
  }

  public void testFStringLiterals() {
    doTest();
  }

  // PY-20770
  public void testAsyncComprehensionsPy36() {
    doTest(LanguageLevel.PYTHON36, true, false);
  }

  // PY-20775
  public void testFStringMissingRightBrace() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest(true, false));
  }

  // PY-20776
  public void testFStringEmptyExpressions() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest(true, false));
  }

  // PY-20778
  public void testFStringIllegalConversionCharacter() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest(true, false));
  }

  // PY-20773
  public void testFStringCommentsBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest(true, false));
  }

  // PY-20844
  public void testFStringBackslashesBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest(true, false));
  }

  // PY-20897
  public void testFStringSingleRightBraces() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest(true, false));
  }

  // PY-20901
  public void testFStringTooDeeplyNestedExpressionFragmentsBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByQuoteOfStringLiteralInFormatPartBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByQuoteOfNestedFStringLiteralInFormatPartBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testMultilineFStringTerminatedByQuotesOfStringLiteralBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testSingleQuotedFStringInsideMultilineFStringTerminatedByLineBreakInExpressionBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByQuoteOfStringLiteralBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByQuoteInsideNestedFStringLiteralInFormatPartBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByQuoteOfNestedFStringLiteralBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByQuoteInsideFStringLiteralBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testNestedMultilineFStringsWithMultilineExpressionsBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByLineBreakInExpressionInFormatPartBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByQuoteOfNestedStringLiteralBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testMultilineFStringTerminatedByQuotesInsideParenthesizedExpressionBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByLineBreakInNestedExpressionInFormatPartBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByLineBreakInExpressionBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByLineBreakInStringLiteralInFormatPartBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByQuoteInsideStringLiteralInFormatPartBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByQuoteInsideNestedStringLiteralBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testMultilineFStringContainingMultilineExpressionAfterStatementBreakBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByQuoteInNestedFormatPartBefore312() { runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false)); }

  // PY-59594
  public void testSingleQuotedFStringInsideMultilineFStringTerminatedByLineBreakInExpressionInParenthesesBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByQuoteInNestedLiteralPartBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByQuoteInsideStringLiteralBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringEqualitySignBefore312() { runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false)); }

  // PY-59594
  public void testFStringTerminatedByLineBreakInNestedExpressionBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByQuoteOfNestedStringLiteralInFormatPartBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByLineBreakInStringLiteralBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testMultilineFStringContainingMultilineExpressionBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByQuoteOfFStringLiteralInFormatPartBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByQuoteInsideNestedFStringLiteralBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByQuoteOfFStringLiteralBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByQuoteInsideFStringLiteralInFormatPartBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-59594
  public void testFStringTerminatedByQuoteInsideNestedStringLiteralInFormatPartBefore312() {
    runWithLanguageLevel(LanguageLevel.PYTHON311, () -> doTest(true, false));
  }

  // PY-12634
  public void testSpaceBetweenAtAndDecorator() {
    doTest(true, true);
  }

  // PY-41305
  public void testExpressionAsDecorator() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doTest);
  }

  // PY-25381
  public void testBuiltinDecorator() {
    doTest(true, true);
  }

  // PY-11418
  public void testFunctionCalls() {
    doTest();
  }

  // PY-20401
  public void testAnnotations() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::doTest);
  }

  // PY-22729
  public void testParametersWithAnnotationsAndDefaults() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doTest);
  }

  // PY-26491
  public void testMultiplePositionalContainers() {
    doTest(LanguageLevel.PYTHON36, true, false);
  }

  // PY-26491
  public void testMultipleKeywordContainers() {
    doTest(LanguageLevel.PYTHON36, true, false);
  }

  // PY-26510
  public void testEmptyRaise() {
    doTest(false, false);
  }

  // PY-28247
  public void testAsyncAndAwaitAsIdentifiersIn37() {
    doTest(LanguageLevel.PYTHON37, false, false);
  }

  // PY-27913
  public void testDunderClassGetItem() {
    doTest(LanguageLevel.PYTHON37, false, true);
  }

  // PY-28313
  public void testVarargs() {
    doTest();
  }

  // PY-28313
  public void testKwargs() {
    doTest();
  }

  // PY-20530
  public void testUnparsedTypeHints() {
    doTest(LanguageLevel.PYTHON36, false, false);
  }

  // PY-32321
  public void testMixedBytesAndNonBytes() {
    doTest(LanguageLevel.PYTHON36, false, false);
  }

  // PY-35512
  public void testInvalidPositionalOnlyParameters() {
    doTest(LanguageLevel.PYTHON38, false, false);
  }

  // PY-35512
  public void testUnsupportedPositionalOnlyParameters() {
    doTest(LanguageLevel.PYTHON37, false, false);
  }

  // PY-33886
  public void testInvalidAssignmentExpressions() {
    doTest(LanguageLevel.PYTHON38, false, false);
  }

  // PY-33886
  public void testUnsupportedAssignmentExpressions() {
    doTest(LanguageLevel.PYTHON37, false, false);
  }

  // PY-36004
  public void testNamedUnicodeBefore38() {
    doTest(LanguageLevel.PYTHON37, false, false);
  }

  // PY-36004
  public void testNamedUnicode() {
    doTest(LanguageLevel.PYTHON38, false, false);
  }

  // PY-36478
  public void testAssignmentExpressionAsATarget() {
    doTest(LanguageLevel.getLatest(), false, false);
  }

  // PY-43619
  public void testAssignmentExpressionInAnIterable() {
    doTest(LanguageLevel.getLatest(), false, false);
  }

  // PY-48008
  public void testMatchAndCaseKeywords() {
    doTest(LanguageLevel.PYTHON310, false, true);
  }

  // PY-24653
  public void testSelfHighlightingInInnerFunc() {
    doTest(LanguageLevel.getLatest(), false, true);
  }

  // PY-24653
  public void testNestedParamHighlightingInInnerFunc() {
    doTest(LanguageLevel.getLatest(), false, true);
  }

  // PY-32302
  public void testLocalVariables() {
    doTest(LanguageLevel.getLatest(), false, true);
  }

  // PY-32302
  public void testVariableAnnotatedWithNonLocalNotHighlightedAsLocal() {
    doTest(LanguageLevel.getLatest(), false, true);
  }

  // PY-32302
  public void testVariableAnnotatedWithGlobalNotHighlightedAsLocal() {
    doTest(LanguageLevel.getLatest(), false, true);
  }

  // PY-61856
  public void testTypeAliasStatement() {
    doTest(LanguageLevel.PYTHON312, false, true);
  }

  // PY-61856
  public void testTypeParameter() {
    doTest(LanguageLevel.PYTHON312, false, true);
  }

  // PY-61856
  public void testTypeAliasStatementBefore312() {
    doTest(LanguageLevel.PYTHON311, false, true);
  }

  // PY-61856
  public void testTypeParameterListBefore312() {
    doTest(LanguageLevel.PYTHON311, false, true);
  }

  public void testParamSpecWithBoundsReported() {
    doTest(LanguageLevel.PYTHON312, true, false);
  }

  // PY-61857
  public void testTypeVarTupleWithBoundsReported() {
    doTest(LanguageLevel.PYTHON312, true, false);
  }

  // PY-61857
  public void testEmptyTypeParameterConstraintReported() {
    doTest(LanguageLevel.PYTHON312, true, false);
  }

  // PY-61857
  public void testOneTypeParameterConstraintReported() {
    doTest(LanguageLevel.PYTHON312, true, false);
  }

  // PY-61857
  public void testTypeParameterDuplicationReported() {
    doTest(LanguageLevel.PYTHON312, true, false);
  }



  @NotNull
  private static EditorColorsScheme createTemporaryColorScheme() {
    EditorColorsManager manager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = (EditorColorsScheme)manager.getGlobalScheme().clone();
    manager.addColorScheme(scheme);
    EditorColorsManager.getInstance().setGlobalScheme(scheme);
    return scheme;
  }

  // ---
  private void doTest(final LanguageLevel languageLevel, final boolean checkWarnings, final boolean checkInfos) {
    runWithLanguageLevel(languageLevel, () -> doTest(checkWarnings, checkInfos));
  }

  private void doTest() {
    doTest(true, true);
  }

  private void doTest(boolean checkWarnings, boolean checkInfos) {
    myFixture.testHighlighting(checkWarnings, checkInfos, false, getTestName(true) + PyNames.DOT_PY);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myOriginalScheme = EditorColorsManager.getInstance().getGlobalScheme();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      EditorColorsManager.getInstance().setGlobalScheme(myOriginalScheme);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/highlighting/";
  }
}
