package com.jetbrains.python;

import com.jetbrains.python.inspections.PyUnresolvedReferencesInspection;

/**
 * User: ktisha
 */
public class AddFieldQuickFixTest extends PyQuickFixTestCase {

  public void testAddClassField() {
    doInspectionTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "FIELD", "A"));
  }

  public void testAddFieldFromMethod() {
    doInspectionTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "y", "A"));
  }

  public void testAddFieldFromInstance() {
    doInspectionTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "y", "A"));
  }

  public void testAddFieldAddConstructor() {
    doInspectionTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "x", "B"));
  }

  public void testAddFieldNewConstructor() {
    doInspectionTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "x", "B"));
  }

}
