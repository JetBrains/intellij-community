// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

import java.util.List;

public class PythonKeywordCompletionTest extends PyTestCase {

  private void doTest3K() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, this::doTest);
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
    doTest3K();
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
    final String testName = "keywordCompletion/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasic();
    final List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertNotNull(lookupElementStrings);
    assertDoesntContain(lookupElementStrings, "continue");
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
    final List<String> lookupElementStrings = doTestByText("try:\n" +
                                                           "  a = 1\n" +
                                                           "<caret>");
    assertNotNull(lookupElementStrings);
    assertDoesntContain(lookupElementStrings, "else");
  }

  public void testElseInCondExpr() {  // PY-2397
    doTest();
  }

  public void testFromDotImport() {  // PY-2772
    doTest3K();
  }

  public void testLambdaInExpression() {  // PY-3150
    doTest();
  }

  public void testNoneInArgList() {  // PY-3464
    doTest3K();
  }

  // PY-5144
  public void testImportKeyword() {
    doTest();
  }

  public void testAsInWith() {  // PY-3701
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> assertTrue(doTestByText("with open(foo) <caret>").contains("as")));
  }

  public void testAsInExcept() {  // PY-1846
    runWithLanguageLevel(
      LanguageLevel.PYTHON27,
      () -> assertTrue(doTestByText("try:\n" +
                                    "    pass\n" +
                                    "except IOError <caret>").contains("as"))
    );
  }

  // PY-13323
  public void testAsInComment() {
    assertDoesntContain(
      doTestByText(
        "import foo\n" +
        "# bar baz\n" +
        "# <caret>"
      ),
      "as"
    );
  }

  public void testElseInFor() {  // PY-6755
    assertTrue(doTestByText("for item in range(10):\n" +
                            "    pass\n" +
                            "el<caret>").contains("else"));
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
    assertDoesntContain(doTestByText("try:\n" +
                                     "    pass\n" +
                                     "except ArithmeticError:\n" +
                                     "    pass\n" +
                                     "else:\n" +
                                     "    pass\n<caret>"), "except");
  }

  public void testExceptAfterFinally() {
    assertDoesntContain(doTestByText("try:\n" +
                                        "    pass\n" +
                                        "except ArithmeticError:\n" +
                                        "    pass\n" +
                                        "finally:\n" +
                                        "    pass\n<caret>"), "except");
  }

  // PY-15075
  public void testImportAfterWhitespaceInRelativeImport() {
    List<String> variants = doTestByText("from ...<caret>");
    assertDoesntContain(variants, "import");

    assertNull(doTestByText("from ... <caret>"));
    myFixture.checkResult("from ... import ");
  }
}
