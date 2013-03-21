package com.jetbrains.python.quickFixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyAttributeOutsideInitInspection;
import org.jetbrains.annotations.NonNls;

/**
 * User: ktisha
 */
@TestDataPath("$CONTENT_ROOT/../testData/quickFixes/PyMoveAttributeToInitQuickFixTest")
public class PyMoveAttributeToInitQuickFixTest extends PyTestCase {

  public void testMoveToInit() {
    doInspectionTest(PyAttributeOutsideInitInspection.class, true);
  }

  public void testCreateInit() {
    doInspectionTest(PyAttributeOutsideInitInspection.class, true);
  }

  public void testAddPass() {
    doInspectionTest(PyAttributeOutsideInitInspection.class, true);
  }

  public void testRemovePass() {
    doInspectionTest(PyAttributeOutsideInitInspection.class, true);
  }

  public void testSkipDocstring() {
    doInspectionTest(PyAttributeOutsideInitInspection.class, true);
  }

  @Override
  @NonNls
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/quickFixes/PyMoveAttributeToInitQuickFixTest";
  }

  protected void doInspectionTest(final Class inspectionClass,
                                  boolean applyFix) {
    final String testFileName = getTestName(true);
    myFixture.enableInspections(inspectionClass);
    myFixture.configureByFile(testFileName + ".py");
    myFixture.checkHighlighting(true, false, false);
    final IntentionAction intentionAction = myFixture.findSingleIntention(PyBundle.message("QFIX.move.attribute"));
    assertNotNull(intentionAction);
    if (applyFix) {
      myFixture.launchAction(intentionAction);
      myFixture.checkResultByFile(testFileName + "_after.py", true);
    }
  }

}
