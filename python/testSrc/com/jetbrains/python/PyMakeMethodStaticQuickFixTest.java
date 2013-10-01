package com.jetbrains.python;

import com.jetbrains.python.inspections.PyMethodMayBeStaticInspection;

/**
 * User: ktisha
 */
public class PyMakeMethodStaticQuickFixTest extends PyQuickFixTestCase {

  public void testOneParam() {
    doInspectionTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testTwoParams() {
    doInspectionTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testEmptyParam() {
    doInspectionTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testFunctionWithDeco() {
    doInspectionTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testDecoWithParams() {
    doInspectionTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testNoSelf() {
    doInspectionTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testUsage() {
    doInspectionTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testUsageImport() {
    doMultifilesTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"), new String[]{"test.py"});
  }

}
