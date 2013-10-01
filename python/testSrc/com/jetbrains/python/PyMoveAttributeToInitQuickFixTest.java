package com.jetbrains.python;

import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.inspections.PyAttributeOutsideInitInspection;

/**
 * User: ktisha
 */
@TestDataPath("$CONTENT_ROOT/../testData/quickFixes/PyMoveAttributeToInitQuickFixTest")
public class PyMoveAttributeToInitQuickFixTest extends PyQuickFixTestCase {

  public void testMoveToInit() {
    doInspectionTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testCreateInit() {
    doInspectionTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testAddPass() {
    doInspectionTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testRemovePass() {
    doInspectionTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testSkipDocstring() {
    doInspectionTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testAddSuperCall() {
    doInspectionTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

  public void testAddSuperCallOldStyle() {
    doInspectionTest(PyAttributeOutsideInitInspection.class, PyBundle.message("QFIX.move.attribute"));
  }

}
