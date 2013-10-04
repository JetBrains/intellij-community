package com.jetbrains.python.quickFixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * User: ktisha
 */
public abstract class PyQuickFixTestCase extends PyTestCase {
  @Override
  @NonNls
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/quickFixes/" + getClass().getSimpleName();
  }

  protected void doQuickFixTest(final Class inspectionClass, final String hint) {
    final String testFileName = getTestName(true);
    myFixture.enableInspections(inspectionClass);
    myFixture.configureByFile(testFileName + ".py");
    myFixture.checkHighlighting(true, false, false);
    final IntentionAction intentionAction = myFixture.findSingleIntention(hint);
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(testFileName + "_after.py", true);
  }

  protected void doInspectionTest(final Class inspectionClass) {
    final String testFileName = getTestName(true);
    myFixture.enableInspections(inspectionClass);
    myFixture.configureByFile(testFileName + ".py");
    myFixture.checkHighlighting(true, false, false);
  }

  protected void doMultifilesTest(@NotNull final Class inspectionClass, @NotNull final String hint, @NotNull final String[] files) {
    final String testFileName = getTestName(true);
    myFixture.enableInspections(inspectionClass);
    String [] filenames = Arrays.copyOf(files, files.length + 1);

    filenames[files.length] = testFileName + ".py";
    myFixture.configureByFiles(filenames);
    final IntentionAction intentionAction = myFixture.findSingleIntention(hint);
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(testFileName + ".py", testFileName + "_after.py", true);
  }
}
