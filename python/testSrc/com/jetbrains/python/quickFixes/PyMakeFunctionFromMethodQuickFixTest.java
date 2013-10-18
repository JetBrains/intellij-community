package com.jetbrains.python.quickFixes;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.inspections.PyMethodMayBeStaticInspection;

/**
 * User: ktisha
 */
public class PyMakeFunctionFromMethodQuickFixTest extends PyQuickFixTestCase {

  public void testOneParam() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }

  public void testTwoParams() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }

  public void testEmptyParam() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }

  public void testFirstMethod() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }

  public void testEmptyStatementList() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }

  public void testNoSelf() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }

  public void testUpdateUsage() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }

  public void testUsageClassCallArgument() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }

  public void testUsageAssignment() {
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
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
    doQuickFixTest(PyMethodMayBeStaticInspection.class, PyBundle.message("QFIX.NAME.make.function"));
  }
}
