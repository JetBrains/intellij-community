package com.jetbrains.python;

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 25.02.2010
 * Time: 12:45:53
 */
public class PyIntentionTest extends PyLightFixtureTestCase {

  private void doTest(String hint) throws Exception {
    myFixture.configureByFile("before" + getTestName(false) + ".py");
    final IntentionAction action = myFixture.findSingleIntention(hint);
    myFixture.launchAction(action);
    myFixture.checkResultByFile("after" + getTestName(false) + ".py");
  }

  private void doTest(String hint, LanguageLevel languageLevel) throws Exception {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), languageLevel);
    try {
      doTest(hint);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/intentions/";
  }

  public void testConvertDictComp() throws Exception {
    doTest(PyBundle.message("INTN.convert.dict.comp.to"));
  }

  public void testConvertSetLiteral() throws Exception {
    doTest(PyBundle.message("INTN.convert.set.literal.to"));
  }

  public void testReplaceExceptPart() throws Exception {
    doTest(PyBundle.message("INTN.convert.except.to"), LanguageLevel.PYTHON30);
  }

  public void testConvertBuiltins() throws Exception {
    doTest(PyBundle.message("INTN.convert.builtin.import"), LanguageLevel.PYTHON30);
  }

  public void testReplaceNotEqOperator() throws Exception {
    doTest(PyBundle.message("INTN.replace.noteq.operator"));
  }

  public void testRemoveLeadingU() throws Exception {
    doTest(PyBundle.message("INTN.remove.leading.u"), LanguageLevel.PYTHON30);
  }

  public void testRemoveTrailingL() throws Exception {
    doTest(PyBundle.message("INTN.remove.trailing.l"), LanguageLevel.PYTHON30);
  }

  public void testReplaceOctalNumericLiteral() throws Exception {
    doTest(PyBundle.message("INTN.replace.octal.numeric.literal"), LanguageLevel.PYTHON30);
  }

  public void testReplaceListComprehensions() throws Exception {
    doTest(PyBundle.message("INTN.replace.list.comprehensions"), LanguageLevel.PYTHON30);
  }

  public void testReplaceRaiseStatement() throws Exception {
    doTest(PyBundle.message("INTN.replace.raise.statement"), LanguageLevel.PYTHON30);
  }

  public void testReplaceBackQuoteExpression() throws Exception {
    doTest(PyBundle.message("INTN.replace.backquote.expression"), LanguageLevel.PYTHON30);
  }

  public void testReplaceMethod() throws Exception {
    doTest(PyBundle.message("INTN.replace.method"), LanguageLevel.PYTHON30);
  }

  public void testSplitIf() throws Exception {
    doTest(PyBundle.message("INTN.split.if.text"));
  }

  public void testNegateComparison() throws Exception {
    doTest(PyBundle.message("INTN.negate.$0.to.$1", "<=", ">"));
  }

  public void testNegateComparison2() throws Exception {
    doTest(PyBundle.message("INTN.negate.$0.to.$1", ">", "<="));
  }

  public void testStringConcatToFormat() throws Exception {
    doTest(PyBundle.message("INTN.replace.plus.with.format.operator"));
  }

  public void testConvertFormatOperatorToMethod() throws Exception {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON30);
  }

  public void testFlipComparison() throws Exception {
    doTest(PyBundle.message("INTN.flip.$0.to.$1", ">", "<"));
  }

}
