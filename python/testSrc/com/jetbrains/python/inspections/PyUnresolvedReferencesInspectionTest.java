package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyUnresolvedReferencesInspectionTest extends PyLightFixtureTestCase {
  private static final String TEST_DIRECTORY = "inspections/PyUnresolvedReferencesInspection/";

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

  public void testImportExceptImportError() {
    doTest();
  }

  public void testConditionalImports() { // PY-983
    myFixture.configureByFiles(TEST_DIRECTORY + getTestName(true) + ".py",
                               TEST_DIRECTORY + "lib1.py",
                               TEST_DIRECTORY + "lib2.py");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testHasattrGuard() { // PY-2309
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(TEST_DIRECTORY + getTestName(true) + ".py");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
