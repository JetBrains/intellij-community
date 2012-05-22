package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author vlan
 */
public class PyTypeCheckerInspectionTest extends PyTestCase {
  private void doTest() {
    myFixture.configureByFile("inspections/PyTypeCheckerInspection/" + getTestName(false) + ".py");
    myFixture.enableInspections(PyTypeCheckerInspection.class);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testOldTests() {
    // TODO: Split these tests into files
    doTest();
  }

  public void testEnumerateIterator() {
    doTest();
  }

  public void testGenericUserClasses() {
    doTest();
  }

  public void testDictGenerics() {
    doTest();
  }

  // PY-6570
  public void testDictLiteralIndexing() {
    doTest();
  }

  // PY-6606
  public void testBuiltinBaseClass() {
    doTest();
  }
}
