package com.jetbrains.python.inspections;

import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;

import java.util.Arrays;

/**
 * User: ktisha
 */
public class PyMethodMayBeStaticInspectionTest extends PyTestCase {

  public void testTruePositive() {
    doTest();
  }

  public void testTrueNegative() {
    doTest();
  }

  public void testEmpty() {
    doTest();
  }

  public void testInit() {
    doTest();
  }

  public void testWithQualifier() {
    doTest();
  }

  public void testStaticMethod() {
    doTest();
  }

  public void testClassMethod() {
    doTest();
  }

  public void testProperty() {
    doTest();
  }

  public void testSelfName() {
    doTest();
  }

  public void testNotImplemented() {
    doTest();
  }

  public void testOverwrittenMethod() {
    doTest();
  }

  public void testSuperMethod() {
    doTest();
  }

  public void testAbstractProperty() {
    doMultiFileTest("abc.py");
  }

  public void testPropertyWithAlias() {
    doMultiFileTest("abc.py");
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(true) + ".py");
    myFixture.enableInspections(PyMethodMayBeStaticInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }

  private void doMultiFileTest(String ... files) {
    String [] filenames = Arrays.copyOf(files, files.length + 1);
    filenames[files.length] = getTestName(true) + ".py";
    myFixture.configureByFiles(filenames);
    myFixture.enableInspections(PyMethodMayBeStaticInspection.class);
    myFixture.checkHighlighting(false, false, true);
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/inspections/PyMethodMayBeStaticInspection/";
  }
}
