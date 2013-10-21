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
package com.jetbrains.python.intentions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.documentation.DocStringFormat;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
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
    super.tearDown();
    if (myDocumentationSettings != null) {
      myDocumentationSettings.setFormat(DocStringFormat.PLAIN);
    }
  }

  private void doTest(String hint) {
    myFixture.configureByFile("intentions/before" + getTestName(false) + ".py");
    final IntentionAction action = myFixture.findSingleIntention(hint);
    myFixture.launchAction(action);
    myFixture.checkResultByFile("intentions/after" + getTestName(false) + ".py");
  }

  private void doTest(String hint, LanguageLevel languageLevel) {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), languageLevel);
    try {
      doTest(hint);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  private void doTest(String hint, boolean ignoreWhiteSpaces) {
    myFixture.configureByFile("intentions/before" + getTestName(false) + ".py");
    final IntentionAction action = myFixture.findSingleIntention(hint);
    myFixture.launchAction(action);
    myFixture.checkResultByFile("intentions/after" + getTestName(false) + ".py", ignoreWhiteSpaces);
  }

  /**
   * Ensures that intention with given hint <i>is not</i> active.
   *
   * @param hint
   */
  private void doNegativeTest(String hint) {
    myFixture.configureByFile("intentions/before" + getTestName(false) + ".py");
    List<IntentionAction> ints = myFixture.filterAvailableIntentions(hint);
    assertEmpty(ints);
  }

  public void testConvertDictComp() {
    setLanguageLevel(LanguageLevel.PYTHON26);
    doTest(PyBundle.message("INTN.convert.dict.comp.to"));
  }

  public void testConvertSetLiteral() {
    setLanguageLevel(LanguageLevel.PYTHON26);
    doTest(PyBundle.message("INTN.convert.set.literal.to"));
  }

  public void testReplaceExceptPart() {
    doTest(PyBundle.message("INTN.convert.except.to"), LanguageLevel.PYTHON30);
  }

  public void testConvertBuiltins() {
    doTest(PyBundle.message("INTN.convert.builtin.import"), LanguageLevel.PYTHON30);
  }

  public void testRemoveLeadingU() {
    doTest(PyBundle.message("INTN.remove.leading.$0", "U"), LanguageLevel.PYTHON30);
  }

  public void testRemoveTrailingL() {
    doTest(PyBundle.message("INTN.remove.trailing.l"), LanguageLevel.PYTHON30);
  }

  public void testReplaceOctalNumericLiteral() {
    doTest(PyBundle.message("INTN.replace.octal.numeric.literal"), LanguageLevel.PYTHON30);
  }

  public void testReplaceListComprehensions() {
    doTest(PyBundle.message("INTN.replace.list.comprehensions"), LanguageLevel.PYTHON30);
  }

  public void testReplaceRaiseStatement() {
    doTest(PyBundle.message("INTN.replace.raise.statement"), LanguageLevel.PYTHON30);
  }

  public void testReplaceBackQuoteExpression() {
    doTest(PyBundle.message("INTN.replace.backquote.expression"), LanguageLevel.PYTHON30);
  }

  /*
  public void testReplaceMethod() {
    doTest(PyBundle.message("INTN.replace.method"), LanguageLevel.PYTHON30);
  }
  */

  public void testSplitIf() {
    doTest(PyBundle.message("INTN.split.if.text"));
  }

  public void testNegateComparison() {
    doTest(PyBundle.message("INTN.negate.$0.to.$1", "<=", ">"));
  }

  public void testNegateComparison2() {
    doTest(PyBundle.message("INTN.negate.$0.to.$1", ">", "<="));
  }

  public void testFlipComparison() {
    doTest(PyBundle.message("INTN.flip.$0.to.$1", ">", "<"));
  }

  public void testReplaceListComprehensionWithFor() {
    doTest(PyBundle.message("INTN.replace.list.comprehensions.with.for"));
  }

  public void testReplaceListComprehension2() {    //PY-6731
    doTest(PyBundle.message("INTN.replace.list.comprehensions.with.for"));
  }

  public void testJoinIf() {
    doTest(PyBundle.message("INTN.join.if.text"));
  }

  public void testJoinIfElse() {
    doNegativeTest(PyBundle.message("INTN.join.if.text"));
  }

  public void testJoinIfBinary() {              //PY-4697
    doTest(PyBundle.message("INTN.join.if.text"));
  }

  public void testJoinIfMultiStatements() {           //PY-2970
    doNegativeTest(PyBundle.message("INTN.join.if.text"));
  }

  public void testDictConstructorToLiteralForm() {
    doTest(PyBundle.message("INTN.convert.dict.constructor.to.dict.literal"));
  }

  public void testDictLiteralFormToConstructor() {
    doTest(PyBundle.message("INTN.convert.dict.literal.to.dict.constructor"));
  }

  public void testDictLiteralFormToConstructor1() {      //PY-2873
    myFixture.configureByFile("intentions/beforeDictLiteralFormToConstructor1" + ".py");
    final IntentionAction action = myFixture.getAvailableIntention(PyBundle.message("INTN.convert.dict.literal.to.dict.constructor"));
    assertNull(action);
  }

  public void testDictLiteralFormToConstructor2() {      //PY-5157
    myFixture.configureByFile("intentions/beforeDictLiteralFormToConstructor2" + ".py");
    final IntentionAction action = myFixture.getAvailableIntention(PyBundle.message("INTN.convert.dict.literal.to.dict.constructor"));
    assertNull(action);
  }

  public void testDictLiteralFormToConstructor3() {
    myFixture.configureByFile("intentions/beforeDictLiteralFormToConstructor3" + ".py");
    final IntentionAction action = myFixture.getAvailableIntention(PyBundle.message("INTN.convert.dict.literal.to.dict.constructor"));
    assertNull(action);
  }

  public void testQuotedString() {      //PY-2915
    doTest(PyBundle.message("INTN.quoted.string.double.to.single"));
  }

  public void testQuotedStringDoubleSlash() {      //PY-3295
    doTest(PyBundle.message("INTN.quoted.string.single.to.double"));
  }

  public void testEscapedQuotedString() { //PY-2656
    doTest(PyBundle.message("INTN.quoted.string.single.to.double"));
  }

  public void testDoubledQuotedString() { //PY-2689
    doTest(PyBundle.message("INTN.quoted.string.double.to.single"));
  }

  public void testMultilineQuotedString() { //PY-8064
    doTest(PyBundle.message("INTN.quoted.string.double.to.single"));
  }

  public void testConvertLambdaToFunction() {
    doTest(PyBundle.message("INTN.convert.lambda.to.function"));
  }

  public void testConvertLambdaToFunction1() {    //PY-6610
    doNegativeTest(PyBundle.message("INTN.convert.lambda.to.function"));
  }

  public void testConvertLambdaToFunction2() {    //PY-6835
    doTest(PyBundle.message("INTN.convert.lambda.to.function"));
  }

  public void testConvertVariadicParam() { //PY-2264
    doTest(PyBundle.message("INTN.convert.variadic.param"));
  }

  public void testConvertTripleQuotedString() { //PY-2697
    doTest(PyBundle.message("INTN.triple.quoted.string"));
  }

  public void testConvertTripleQuotedString1() { //PY-7774
    doTest(PyBundle.message("INTN.triple.quoted.string"));
  }

  public void testConvertTripleQuotedStringInParenthesized() { //PY-7883
    doTest(PyBundle.message("INTN.triple.quoted.string"));
  }

  public void testConvertTripleQuotedUnicodeString() { //PY-7152
    doTest(PyBundle.message("INTN.triple.quoted.string"));
  }

  public void testConvertTripleQuotedParenthesizedString() { //PY-7151
    doTest(PyBundle.message("INTN.triple.quoted.string"));
  }

  public void testTransformConditionalExpression() { //PY-3094
    doTest(PyBundle.message("INTN.transform.into.if.else.statement"));
  }

  public void testImportFromToImport() {
    doTest("Convert to 'import sys'");
  }

  public void testTypeInDocstring() {
    doDocReferenceTest();
  }

  public void testTypeInDocstring3() {
    doDocReferenceTest();
  }

  public void testTypeInDocstring4() {
    doDocReferenceTest();
  }

  public void testTypeInDocstringParameterInCallable() {
    doDocReferenceTest();
  }

  public void testTypeInDocstring5() {
    doDocReferenceTest();
  }

  public void testTypeInDocstring6() {         //PY-7973
    doNegativeTest(PyBundle.message("INTN.specify.return.type"));
  }

  public void testTypeInDocstring7() {         //PY-8930
    doDocReferenceTest();
  }

  public void testReturnTypeInDocstring() {
    doDocReturnTypeTest();
  }

  public void testTypeInDocstring1() {
    doDocReturnTypeTest();
  }

  public void testTypeInDocstring2() {
    doDocReturnTypeTest();
  }

  public void testTypeInPy3Annotation() {      //PY-7045
    doTypeAnnotationTest();
  }

  public void testReturnTypeInPy3Annotation() {      //PY-7085
    doTest(PyBundle.message("INTN.specify.return.type.in.annotation"), LanguageLevel.PYTHON32);
  }

  public void testReturnTypeInPy3Annotation1() {      //PY-8783
    doTest(PyBundle.message("INTN.specify.return.type.in.annotation"), LanguageLevel.PYTHON32);
  }

  public void testReturnTypeInPy3Annotation2() {      //PY-8783
    doTest(PyBundle.message("INTN.specify.return.type.in.annotation"), LanguageLevel.PYTHON32);
  }

  public void testTypeAnnotation3() {  //PY-7087
    doTypeAnnotationTest();
  }

  private void doTypeAnnotationTest() {
    doTest(PyBundle.message("INTN.specify.type.in.annotation"), LanguageLevel.PYTHON32);
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
    setLanguageLevel(LanguageLevel.PYTHON33);
    try {
      doNegativeTest(PyBundle.message("INTN.insert.assertion"));
    }
    finally {
      setLanguageLevel(null);
    }
  }

  public void testTypeAssertion4() {  //PY-7971
    doTestTypeAssertion();
  }

  private void doTestTypeAssertion() {
    doTest(PyBundle.message("INTN.insert.assertion"));
  }

  public void testDocStub() {
    doDocStubTest();
  }

  public void testOneLineDocStub() {
    doDocStubTest();
  }

  public void testDocStubKeywordOnly() {
    doDocStubTest(LanguageLevel.PYTHON32);
  }

  // PY-7383
  public void testYieldFrom() {
    doTest(PyBundle.message("INTN.yield.from"), LanguageLevel.PYTHON33);
  }

  public void testConvertStaticMethodToFunction() {
    doTest(PyBundle.message("INTN.convert.static.method.to.function"));
  }

  public void testConvertStaticMethodToFunctionUsage() {
    doTest(PyBundle.message("INTN.convert.static.method.to.function"));
  }

  private void doDocStubTest(LanguageLevel languageLevel) {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), languageLevel);
    try {
      doDocStubTest();
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  private void doDocStubTest() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    codeInsightSettings.JAVADOC_STUB_ON_ENTER = true;
    doTest(PyBundle.message("INTN.doc.string.stub"), true);
  }

  private void doDocReferenceTest() {
    doTest(PyBundle.message("INTN.specify.type"));
  }

  private void doDocReturnTypeTest() {
    doTest(PyBundle.message("INTN.specify.return.type"));
  }

}
