package com.jetbrains.python.quickFixes;

import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.PyAttributeOutsideInitInspection;

/**
 * User: ktisha
 */
@TestDataPath("$CONTENT_ROOT/../testData/quickFixes/PyMoveAttributeToInitQuickFixTest")
public class PyMoveAttributeToInitQuickFixTest extends PyQuickFixTestCase {

  public void testMoveToInit() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testCreateInit() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testAddPass() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testRemovePass() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testSkipDocstring() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testAddSuperCall() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testAddSuperCallOldStyle() {
    doQuickFixTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testPropertyNegative() {
    doInspectionTest(PyAttributeOutsideInitInspection.class);
  }

}
