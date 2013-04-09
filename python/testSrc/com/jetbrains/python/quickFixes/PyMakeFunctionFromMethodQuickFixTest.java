package com.jetbrains.python.quickFixes;

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

}
