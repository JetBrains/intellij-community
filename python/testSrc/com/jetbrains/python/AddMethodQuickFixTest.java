package com.jetbrains.python;

import com.jetbrains.python.inspections.PyClassHasNoInitInspection;
import com.jetbrains.python.inspections.PyUnresolvedReferencesInspection;

/**
 * User: ktisha
 */
public class AddMethodQuickFixTest extends PyQuickFixTestCase {

  public void testAddInit() {
    doInspectionTest(PyClassHasNoInitInspection.class, PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", "__init__", "A"));
  }

  public void testAddInitAfterDocstring() {
    doInspectionTest(PyClassHasNoInitInspection.class, PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", "__init__", "A"));
  }

  public void testAddMethodReplacePass() {
    doInspectionTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", "y", "A"));
  }

  public void testAddMethodFromInstance() {
    doInspectionTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", "y", "A"));
  }

  public void testAddMethodFromMethod() {
    doInspectionTest(PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", "y", "A"));
  }

}
