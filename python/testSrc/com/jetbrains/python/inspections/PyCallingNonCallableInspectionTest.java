package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author yole
 */
public class PyCallingNonCallableInspectionTest extends PyTestCase {
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

  // PY-3892
  public void testCallableCheck() {
    doTest();
  }
  
  public void testClassMethodFirstParam() {
    doTest();
  }

  // PY-4090
  public void testDecorators() {
    doTest();
  }

  // PY-4608
  public void testUnionType() {
    doTest();
  }

  // PY-8416
  public void testCallAttributeAssignment() {
    doTest();
  }

  // PY-5905
  public void testCallableClassDecorator() {
    doTest();
  }

  // PY-8182
  public void testGetattrCallable() {
    doTest();
  }

  // PY-8801
  public void testQualifiedNamedTuple() {
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
