package com.jetbrains.python;

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

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
    doTest(PyBundle.message("INTN.convert.except.to"));
  }

  public void testConvertBuiltins() throws Exception {
    doTest(PyBundle.message("INTN.convert.builtin.import"));
  }
}
