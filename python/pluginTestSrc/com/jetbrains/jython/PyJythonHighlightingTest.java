package com.jetbrains.jython;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.inspections.PyCallingNonCallableInspection;
import com.jetbrains.python.inspections.PyUnresolvedReferencesInspection;

/**
 * @author yole
 */
public class PyJythonHighlightingTest extends LightCodeInsightFixtureTestCase {
  public void testCallableJavaClass() {
    doCallableTest();
  }

  public void testCallableStaticMethod() {
    doCallableTest();
  }

  private void doCallableTest() {
    myFixture.configureByFile(getTestName(false) + ".py");
    myFixture.enableInspections(PyCallingNonCallableInspection.class, PyUnresolvedReferencesInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }


  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/highlighting/jython/";
  }
}
