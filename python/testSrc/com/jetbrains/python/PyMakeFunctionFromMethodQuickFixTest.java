package com.jetbrains.python;

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
    doMultifilesTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"), new String[]{"test.py"});
  }

  public void testUsageImport1() {
    doMultifilesTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"), new String[]{"test.py"});
  }

  public void testUsageImport2() {
    doMultifilesTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"), new String[]{"test.py"});
  }

  public void testUsageSelf() {
    doInspectionTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }
}
