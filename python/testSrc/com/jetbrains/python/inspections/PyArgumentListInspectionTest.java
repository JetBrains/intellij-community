package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyArgumentListInspectionTest extends PyLightFixtureTestCase {
  public void testInstanceMethodAsLambda() {
    doTest();
  }

  public void testClassMethodMultipleDecorators() {
    doTest();
  }

  public void testTuples() {  // PY-2294
    doTest();
  }

  public void testNestedClass() {  // PY-2460
    doTest();
  }

  public void testReassignedMethod() {  // PY-2622
    doTest();
  }

  public void testConstructorQualifiedByModule() {
    doTest();
  }

  public void testFunctionStoredInInstance() {  // PY-3623
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile("inspections/PyArgumentListInspection/" + getTestName(true) + ".py");
    myFixture.enableInspections(PyArgumentListInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
