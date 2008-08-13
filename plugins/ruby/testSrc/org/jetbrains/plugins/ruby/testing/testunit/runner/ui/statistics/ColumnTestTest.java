package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import org.jetbrains.plugins.ruby.testing.testunit.runner.BaseRUnitTestsTestCase;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;

/**
 * @author Roman Chernyatchik
 */
public class ColumnTestTest extends BaseRUnitTestsTestCase {
  private ColumnTest myColumnTest;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myColumnTest = new ColumnTest();
  }

  public void testValueOf_Test() {
    assertEquals("test", myColumnTest.valueOf(createTestProxy("test")));

    final RTestUnitTestProxy test = createTestProxy("test of suite", mySuite);
    assertEquals("test of suite", myColumnTest.valueOf(test));
  }

  public void testValueOf_TestNameCollapsing() {
    assertEquals("test", myColumnTest.valueOf(createTestProxy("test")));

    final RTestUnitTestProxy suiteProxy = createSuiteProxy("MySuite");
    assertEquals("test of suite", myColumnTest.valueOf(createTestProxy("MySuite.test of suite", suiteProxy)));
    assertEquals("test of suite", myColumnTest.valueOf(createTestProxy("MySuite test of suite", suiteProxy)));
    assertEquals("Not MySuite test of suite", myColumnTest.valueOf(createTestProxy("Not MySuite test of suite", suiteProxy)));
  }

  public void testValueOf_Suite() {
    final RTestUnitTestProxy suite = createSuiteProxy("my suite", mySuite);
    createTestProxy("test", suite);
    assertEquals("my suite", myColumnTest.valueOf(suite));
  }

  public void testValueOf_SuiteNameCollapsing() {
    final RTestUnitTestProxy suiteProxy = createSuiteProxy("MySuite");
    assertEquals("child suite", myColumnTest.valueOf(createSuiteProxy("MySuite.child suite", suiteProxy)));
    assertEquals("child suite", myColumnTest.valueOf(createSuiteProxy("MySuite child suite", suiteProxy)));
    assertEquals("Not MySuite child suite", myColumnTest.valueOf(createSuiteProxy("Not MySuite child suite", suiteProxy)));
  }
}
