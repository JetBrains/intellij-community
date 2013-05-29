package com.jetbrains.python.quickFixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.PyMethodMayBeStaticInspection;

/**
 * User: ktisha
 */
public class PyMakeFunctionFromMethodQuickFixTest extends PyQuickFixTestCase {

  public void testOneParam() {
    doInspectionTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }

  public void testTwoParams() {
    doInspectionTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }

  public void testEmptyParam() {
    doInspectionTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }

  public void testFirstMethod() {
    doInspectionTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }

  public void testEmptyStatementList() {
    doInspectionTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }

  public void testNoSelf() {
    doInspectionTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }

  public void testUpdateUsage() {
    doInspectionTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }

  public void testUsageClassCallArgument() {
    doInspectionTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }

  public void testUsageAssignment() {
    doInspectionTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }

  public void testUsageImport() {
    doTest();
  }

  public void testUsageImport1() {
    doTest();
  }


  protected void doTest() {
    final String testFileName = getTestName(true);
    myFixture.enableInspections(PyMethodMayBeStaticInspection.class);
    myFixture.configureByFiles("test.py", testFileName + ".py");
    final IntentionAction intentionAction = myFixture.findSingleIntention(PyBundle.message("QFIX.NAME.make.function"));
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(testFileName + ".py", testFileName + "_after.py", true);
  }
}
