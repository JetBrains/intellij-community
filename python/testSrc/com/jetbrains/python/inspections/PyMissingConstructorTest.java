package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author yole
 */
public class PyMissingConstructorTest extends PyTestCase {
  private static final String TEST_DIRECTORY = "inspections/PyMissingConstructorInspection/";
  
  public void testBasic() {
    doTest();
  }
  
  public void testQualifiedName() { // PY-3278
    doTest();
  }
  
  public void testNoConstructor() {  // PY-3238
    doTest();
  }
  
  public void testDeepInheritance() {  // PY-3313
    doTest();
  }
  
  public void testInheritFromSelf() {  // PY-3395
    doTest();
  }

  public void testDunderClass() {  // PY-4038
    doTest();
  }

  public void testException() { // PY-7176
    doTest();
  }

  public void testInnerClass() { //PY-7699
    doTest();
  }
  
  public void testPy3k() {
    setLanguageLevel(LanguageLevel.PYTHON30);
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(TEST_DIRECTORY + getTestName(true) + ".py");
    myFixture.enableInspections(PyMissingConstructorInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
