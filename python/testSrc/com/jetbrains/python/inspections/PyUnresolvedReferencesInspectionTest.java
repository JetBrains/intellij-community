package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public class PyUnresolvedReferencesInspectionTest extends PyTestCase {
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

  public void testMro() {  // PY-3989
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

  public void testOperators() {
    doTest();
  }

  public void testNamedTuple() {
    doTest();
  }

  // PY-2308
  public void testTypeAssertions() {
    doTest();
  }
  
  public void testUnresolvedImportedModule() {  // PY-2075
    doTest();
  }
  
  public void testSuperType() {  // PY-2320
    doTest();
  }

  public void testImportFunction() {  // PY-1896
    doTest();
  }
  
  public void testSuperclassAsLocal() {  // PY-5427
    doTest();
  }

  public void testImportToContainingFile() {  // PY-4372
    doMultiFileTest();
  }

  public void testFromImportToContainingFile() {  // PY-4371
    doMultiFileTest();
  }

  public void testFromImportToContainingFile2() {  // PY-5945
    doMultiFileTest();
  }

  private void doMultiFileTest() {
    myFixture.copyFileToProject("inspections/PyUnresolvedReferencesInspection/__init__.py", "PyUnresolvedReferencesInspection/__init__.py");
    myFixture.copyFileToProject("inspections/PyUnresolvedReferencesInspection/" + getTestName(true) + ".py",
                                "PyUnresolvedReferencesInspection/" + getTestName(true) + ".py");
    myFixture.configureFromTempProjectFile("PyUnresolvedReferencesInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testFromPackageImportBuiltin() {
    myFixture.copyDirectoryToProject("inspections/PyUnresolvedReferencesInspection/fromPackageImportBuiltin", "");
    myFixture.configureFromTempProjectFile("fromPackageImportBuiltin.py");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }

  private void doTest() {
    myFixture.configureByFile(TEST_DIRECTORY + getTestName(true) + ".py");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
