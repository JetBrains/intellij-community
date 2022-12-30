// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.jetbrains.python.codeInsight.completion.PyModuleNameCompletionContributor;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

import java.util.List;

public class PythonKeywordCompletionTest extends PyTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PyModuleNameCompletionContributor.ENABLED = false;
  }

  private void doTest() {
    CamelHumpMatcher.forceStartMatching(myFixture.getTestRootDisposable());
    final String testName = "keywordCompletion/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + ".after.py");
  }

  private List<String> doTestByText(String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    myFixture.completeBasic();
    return myFixture.getLookupElementStrings();
  }

  private List<String> doTestByTestName() {
    final String testName = "keywordCompletion/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasic();
    return myFixture.getLookupElementStrings();
  }

  public void testKeywordAfterComment() {  // PY-697
    doTest();
  }

  public void testEmptyFile() {  // PY-1845
    myFixture.configureByText(PythonFileType.INSTANCE, "");
    myFixture.completeBasic();
    final List<String> elements = myFixture.getLookupElementStrings();
    assertNotNull(elements);
    assertTrue(elements.contains("import"));
  }

  public void testNonlocal() {  // PY-2289
    doTest();
  }

  public void testYield() {
    doTest();
  }

  public void testElse() {
    doTest();
  }

  public void testElseNotIndented() {
    doTest();
  }

  public void testElseInTryNotIndented() {
    doTest();
  }

  public void testElif() {
    doTest();
  }

  public void testElifNotIndented() {
    doTest();
  }

  public void testExcept() {
    doTest();
  }

  public void testExceptNotIndented() {
    doTest();
  }

  public void testFinallyInExcept() {
    doTest();
  }

  public void testContinue() {
    doTest();
  }

  public void testNoContinueInFinally() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      final String testName = "keywordCompletion/" + getTestName(true);
      myFixture.configureByFile(testName + ".py");
      myFixture.completeBasic();
      final List<String> lookupElementStrings = myFixture.getLookupElementStrings();
      assertNotNull(lookupElementStrings);
      assertDoesntContain(lookupElementStrings, "continue");
    });
  }

  public void testNoElifBeforeElse() {
    final String testName = "keywordCompletion/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasic();
    final List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertNotNull(lookupElementStrings);
    assertDoesntContain(lookupElementStrings, "elif");
  }

  public void testNoElseBeforeExcept() {
    final List<String> lookupElementStrings = doTestByText("""
                                                             try:
                                                               a = 1
                                                             <caret>""");
    assertNotNull(lookupElementStrings);
    assertDoesntContain(lookupElementStrings, "else");
  }

  public void testElseInCondExpr() {  // PY-2397
    doTest();
  }

  public void testFromDotImport() {  // PY-2772
    doTest();
  }

  public void testLambdaInExpression() {  // PY-3150
    doTest();
  }

  public void testNoneInArgList() {  // PY-3464
    doTest();
  }

  // PY-5144
  public void testImportKeyword() {
    doTest();
  }

  public void testAsInWith() {  // PY-3701
    assertTrue(doTestByText("with open(foo) <caret>").contains("as"));
  }

  public void testAsInExcept() {  // PY-1846
    assertTrue(doTestByText("""
                              try:
                                  pass
                              except IOError <caret>""").contains("as"));
  }

  // PY-13323
  public void testAsInComment() {
    assertDoesntContain(
      doTestByText(
        """
          import foo
          # bar baz
          # <caret>"""
      ),
      "as"
    );
  }

  public void testElseInFor() {  // PY-6755
    assertTrue(doTestByText("""
                              for item in range(10):
                                  pass
                              el<caret>""").contains("else"));
  }

  public void testFinallyInElse() {  // PY-6755
    doTest();
  }

  public void testForInComprehension() {  // PY-3687
    assertContainsElements(doTestByText("L = [x fo<caret>]"), "for");
    assertContainsElements(doTestByText("L = [x <caret>]"), "for");
    assertContainsElements(doTestByText("L = [x <caret> x in y]"), "for");

    assertDoesntContain(doTestByText("L = [<caret>]"), "for");
    assertDoesntContain(doTestByText("L = [x for x <caret>]"), "for");
    assertDoesntContain(doTestByText("L = [x for x <caret> in y]"), "for");
  }

  public void testInInComprehension() {  // PY-3687
    assertContainsElements(doTestByText("L = [x for x <caret>]"), "in");
    assertContainsElements(doTestByText("L = [x for x i<caret>]"), "in");
    assertContainsElements(doTestByText("L = [x for x i<caret>n y]"), "in");

    assertDoesntContain(doTestByText("L = [<caret>]"), "in");
    assertDoesntContain(doTestByText("L = [x <caret> for]"), "in");
    assertDoesntContain(doTestByText("L = [x <caret>]"), "in");
  }

  public void testInInFor() {  // PY-10248
    assertContainsElements(doTestByText("for x <caret>"), "in");
    assertContainsElements(doTestByText("for x i<caret>"), "in");
    assertContainsElements(doTestByText("for x i<caret>n y:\n  pass]"), "in");
  }

  // PY-11375
  public void testYieldExpression() {
    assertContainsElements(doTestByText("def gen(): x = <caret>"), "yield");
    assertDoesntContain(doTestByText("def gen(): x = 1 + <caret>"), "yield");
    assertContainsElements(doTestByText("def gen(): x = 1 + (<caret>"), "yield");
    assertContainsElements(doTestByText("def gen(): x **= <caret>"), "yield");
    assertDoesntContain(doTestByText("def gen(): func(<caret>)"), "yield");
    assertContainsElements(doTestByText("def gen(): func((<caret>"), "yield");
    assertDoesntContain(doTestByText("def gen(): x = y<caret> = 42"), "yield");
  }

  public void testExceptAfterElse() {
    assertDoesntContain(doTestByText("""
                                       try:
                                           pass
                                       except ArithmeticError:
                                           pass
                                       else:
                                           pass
                                       <caret>"""), "except");
  }

  public void testExceptAfterFinally() {
    assertDoesntContain(doTestByText("""
                                       try:
                                           pass
                                       except ArithmeticError:
                                           pass
                                       finally:
                                           pass
                                       <caret>"""), "except");
  }

  // PY-15075
  public void testImportAfterWhitespaceInRelativeImport() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      List<String> variants = doTestByText("from ...<caret>");
      assertDoesntContain(variants, "import");

      assertNull(doTestByText("from ... <caret>"));
      myFixture.checkResult("from ... import ");
    });
  }

  // PY-7018
  public void testNoNotAndLambdaAfterTargetQualifier() {
    assertDoesntContain(doTestByTestName(), "not", "lambda");
  }

  // PY-13111
  public void testNoForAndYieldInCommentContext() {
    assertDoesntContain(doTestByTestName(), "for", "yield");
  }

  // PY-45368
  public void testNoneInParameterAnnotation() {
    doTest();
  }

  // PY-45368
  public void testNoneInReturnAnnotation() {
    doTest();
  }

  // PY-48039
  public void testMatchInsideFunction() {
    doTest();
  }

  // PY-48039
  public void testMatchOnTopLevel() {
    doTest();
  }

  // PY-48039
  public void testNoMatchInsideArgumentList() {
    doTest();
  }

  // PY-48039
  public void testNoMatchInCondition() {
    doTest();
  }

  // PY-48039
  public void testNoMatchAfterQualifier() {
    doTest();
  }

  // PY-48039
  public void testNoMatchBefore310() {
    runWithLanguageLevel(LanguageLevel.PYTHON39, this::doTest);
  }

  // PY-48039
  public void testCaseInsideMatchStatement() {
    doTest();
  }

  // PY-48039
  public void testNoCaseBefore310() {
    runWithLanguageLevel(LanguageLevel.PYTHON39, this::doTest);
  }

  // PY-48039
  public void testNoCaseOutsideMatchStatement() {
    doTest();
  }

  // PY-49728
  public void testNoNonLiteralExpressionKeywordsInsidePattern() {
    List<String> variants = doTestByTestName();
    assertDoesntContain(variants, PyNames.ASYNC, PyNames.NOT, PyNames.LAMBDA);
    assertContainsElements(variants, PyNames.NONE, PyNames.TRUE, PyNames.FALSE);
  }

  // PY-49728
  public void testNoNonLiteralExpressionKeywordsAfterPattern() {
    List<String> variants = doTestByTestName();
    assertDoesntContain(variants, PyNames.ASYNC, PyNames.NOT, PyNames.LAMBDA);
    assertContainsElements(variants, PyNames.NONE, PyNames.TRUE, PyNames.FALSE);
  }

  // PY-49728
  public void testNonLiteralExpressionKeywordsInGuardCondition() {
    assertContainsElements(doTestByTestName(), PyNames.ASYNC, PyNames.NOT, PyNames.LAMBDA);
  }

  // PY-49728
  public void testNonLiteralExpressionKeywordsInCaseClauseBody() {
    assertContainsElements(doTestByTestName(), PyNames.ASYNC, PyNames.NOT, PyNames.LAMBDA);
  }
}
