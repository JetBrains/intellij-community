package com.jetbrains.jython;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.inspections.PyCallingNonCallableInspection;

/**
 * @author yole
 */
public class PyJythonHighlightingTest extends LightCodeInsightFixtureTestCase {
  public void testCallableJavaClass() {
    myFixture.configureByFile("callableJavaClass.py");
    myFixture.enableInspections(PyCallingNonCallableInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }


  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/highlighting/jython/";
  }
}
