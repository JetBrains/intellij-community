package com.jetbrains.python.quickFixes;

import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.inspections.PyAugmentAssignmentInspection;

/**
 * User: ktisha
 */
public class PyAugmentAssignmentQuickFixTest extends PyQuickFixTestCase {

  public void testSimple() {  // PY-1415
    doInspectionTest(PyAugmentAssignmentInspection.class);
  }

  public void testWithContext() {  // PY-2481
    doInspectionTest(PyAugmentAssignmentInspection.class);
  }

  public void testPercent() {  // PY-3197
    doInspectionTest(PyAugmentAssignmentInspection.class);
  }

  public void testDivision() {  // PY-5037
    doInspectionTest(PyAugmentAssignmentInspection.class);
  }

  public void testReferences() {  // PY-6331
    doInspectionTest(PyAugmentAssignmentInspection.class);
  }

  public void testFunction() {  // PY-6678
    doInspectionTest(PyAugmentAssignmentInspection.class);
  }

  public void testSubscription() {  // PY-7715
    doInspectionTest(PyAugmentAssignmentInspection.class);
  }
}
