package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public class PyUnusedImportTest extends PyTestCase {
  public void testModuleAndSubmodule() {  // PY-3626
    myFixture.copyDirectoryToProject("inspections/unusedImport/moduleAndSubmodule", "");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.testHighlighting(true, false, false, "py3626.py");
  }

  public void testSubpackageInInitPy() {  // PY-3201
    myFixture.copyDirectoryToProject("inspections/unusedImport/subpackageInInitPy", "");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.testHighlighting(true, false, false, "package1/__init__.py");
  }

  // PY-5589
  public void testUnusedPackageAndSubmodule() {
    myFixture.copyDirectoryToProject("inspections/unusedImport/unusedPackageAndSubmodule", "");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.testHighlighting(true, false, false, "test1.py");
  }

  // PY-5621
  public void testUnusedSubmodule() {
    myFixture.copyDirectoryToProject("inspections/unusedImport/unusedSubmodule", "");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.testHighlighting(true, false, false, "test1.py");
  }
}
