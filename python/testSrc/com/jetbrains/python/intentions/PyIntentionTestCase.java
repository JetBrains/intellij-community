package com.jetbrains.python.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NonNls;

/**
 * User: ktisha
 */
public abstract class PyIntentionTestCase extends PyTestCase {
  @Override
  @NonNls
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/intentions/" + getClass().getSimpleName();
  }


  protected void doIntentionTest(final String hint) {
    final String testFileName = getTestName(true);
    myFixture.configureByFile(testFileName + ".py");
    final IntentionAction intentionAction = myFixture.findSingleIntention(hint);
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(testFileName + "_after.py", true);
  }

  protected void doNegateIntentionTest(final String hint) {
    final String testFileName = getTestName(true);
    myFixture.configureByFile(testFileName + ".py");
    final IntentionAction intentionAction = myFixture.getAvailableIntention(hint);
    assertNull(intentionAction);
  }
}
