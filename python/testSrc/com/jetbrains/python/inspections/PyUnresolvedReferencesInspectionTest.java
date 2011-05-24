package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyUnresolvedReferencesInspectionTest extends PyLightFixtureTestCase {
  public void testSelfReference() {
    doTest();
  }

  public void testUnresolvedImport() {
    doTest();
  }

  public void testStaticMethodParameter() {  // PY-663
    doTest();
  }

  public void testOverridesGetAttr() {  // PY-574
    doTest();
  }

  public void testUndeclaredAttrAssign() {  // PY-906
    doTest();
  }

  public void testSlots() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("inspections/PyUnresolvedReferencesInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
