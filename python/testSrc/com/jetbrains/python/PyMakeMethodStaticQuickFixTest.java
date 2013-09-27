package com.jetbrains.python;

import com.jetbrains.python.inspections.PyMethodMayBeStaticInspection;

/**
 * User: ktisha
 */
public class PyMakeMethodStaticQuickFixTest extends PyQuickFixTestCase {

  public void testOneParam() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testTwoParams() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testEmptyParam() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testFunctionWithDeco() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testDecoWithParams() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testNoSelf() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testUsage() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"));
  }

  public void testUsageImport() {
    doMultifilesTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.static"), new String[]{"test.py"});
  }

}
