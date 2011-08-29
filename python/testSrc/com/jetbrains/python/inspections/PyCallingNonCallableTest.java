package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author yole
 */
public class PyCallingNonCallableTest extends PyLightFixtureTestCase {
  public void testTupleNonCallable() {
    doTest();
  }
  
  public void testStaticMeth() {
    doTest();
  }
  
  public void testConcealer() {
    doTest();
  }
  
  public void testModule() {
    doTest();
  }
  
  public void testClassAsCallable() {  // PY-4061
    doTest();
  }
  
  public void testClassAssignments() {  // PY-4061
    doTest();
  }
  
  public void testNamedTupleCallable() {
    doTest();
  }
  
  public void testClassMethodFirstParam() {
    doTest();
  }
  
  private void doTest() {
    setLanguageLevel(LanguageLevel.PYTHON27);
    try {
      myFixture.configureByFile("inspections/PyCallingNonCallableInspection/" + getTestName(true) + ".py");
      myFixture.enableInspections(PyCallingNonCallableInspection.class);
      myFixture.checkHighlighting(true, false, false);
    }
    finally {
      setLanguageLevel(null);
    }
  }
}
