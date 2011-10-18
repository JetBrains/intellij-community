package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public class PyUnusedImportTest extends PyTestCase {
  public void _testModuleAndSubmodule() {  // PY-3626
    myFixture.copyDirectoryToProject("inspections/unusedImport/moduleAndSubmodule", "");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.testHighlighting(true, false, false, "py3626.py");
  }

  public void testSubpackageInInitPy() {  // PY-3201
    myFixture.copyDirectoryToProject("inspections/unusedImport/subpackageInInitPy", "");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.testHighlighting(true, false, false, "package1/__init__.py");
  }
}
