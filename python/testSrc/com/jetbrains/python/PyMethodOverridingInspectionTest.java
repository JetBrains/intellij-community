package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyMethodOverridingInspection;

/**
 * @author vlan
 */
public class PyMethodOverridingInspectionTest extends PyTestCase {
  private static final String TEST_DIRECTORY = "inspections/PyMethodOverridingInspection/";

  public void testArgsKwargsOverrideArg() {
    doTest();
  }

  public void testNotOverridingMethod() {
    doTest();
  }

  public void testInitNew() {
    doTest();
  }

  public void testArgsKwargsAsAllowAnything() {
    doTest();
  }

  // PY-1083
  public void testExtraKwargs() {
    doTest();
  }

  // PY-6700
  public void testBothArgsKwargs() {
    doTest();
  }

  // PY-6700
  public void testArgAndKwargs() {
    doTest();
  }

  // PY-7157
  public void testDefaultArgument() {
    doTest();
  }

  // PY-7162
  public void testLessArgumentsPlusDefaults() {
    doTest();
  }

  public void testLessParametersAndKwargs() {
    doTest();
  }

  // PY-7159
  public void testRequiredParameterAndKwargs() {
    doTest();
  }

  // PY-7725
  public void testPropertySetter() {
    doTest();
  }

  // PY-10229
  public void testInstanceCheck() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(TEST_DIRECTORY + getTestName(false) + ".py");
    myFixture.enableInspections(PyMethodOverridingInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }
}
