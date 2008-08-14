package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import org.jetbrains.plugins.ruby.testing.testunit.runner.BaseRUnitTestsTestCase;
import org.jetbrains.plugins.ruby.testing.testunit.runner.RTestUnitTestProxy;

/**
 * @author Roman Chernyatchik
 */
public class ColumnDurationTest extends BaseRUnitTestsTestCase {
  private ColumnDuration myColumnDuration;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myColumnDuration = new ColumnDuration();
  }

  public void testValueOf_NotRun() {
    assertEquals("<NOT RUN>", myColumnDuration.valueOf(mySimpleTest));
  }

  public void testValueOf_InProgress() {
    mySimpleTest.setStarted();
    assertEquals("<RUNNING>", myColumnDuration.valueOf(mySimpleTest));
  }

  public void testValueOf_TestFailure() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    assertEquals("<UNKNOWN>", myColumnDuration.valueOf(mySimpleTest));

    mySimpleTest.setDuration(10000);
    assertEquals(String.valueOf((float)10) + " s", myColumnDuration.valueOf(mySimpleTest));
  }

  public void testValueOf_TestPassed() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    assertEquals("<UNKNOWN>", myColumnDuration.valueOf(mySimpleTest));

    mySimpleTest.setDuration(10000);
    assertEquals(String.valueOf((float)10) + " s", myColumnDuration.valueOf(mySimpleTest));
  }

  public void testValueOf_TestError() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);
    assertEquals("<UNKNOWN>", myColumnDuration.valueOf(mySimpleTest));

    mySimpleTest.setDuration(10000);
    assertEquals(String.valueOf((float)10) + " s", myColumnDuration.valueOf(mySimpleTest));
  }

  public void testValueOf_TestTerminated() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();
    assertEquals("<TERMINATED>", myColumnDuration.valueOf(mySimpleTest));

    mySimpleTest.setDuration(10000);
    assertEquals("TERMINATED: " + String.valueOf((float)10) + " s", myColumnDuration.valueOf(mySimpleTest));
  }

  public void testValueOf_SuiteEmpty() {
    final RTestUnitTestProxy suite = createSuiteProxy();
    suite.setStarted();
    suite.setFinished();
    assertEquals("<NO TESTS>", myColumnDuration.valueOf(suite));

    suite.setFinished();
    assertEquals("<NO TESTS>", myColumnDuration.valueOf(suite));
  }

  public void testValueOf_SuiteNotRun() {
    final RTestUnitTestProxy suite = createSuiteProxy();
    assertEquals("<NOT RUN>", myColumnDuration.valueOf(suite));

    final RTestUnitTestProxy test = createTestProxy("test", suite);
    assertEquals("<NOT RUN>", myColumnDuration.valueOf(suite));

    test.setDuration(5);
    assertEquals("<NOT RUN>", myColumnDuration.valueOf(suite));
  }

  public void testValueOf_SuiteFailed() {
    final RTestUnitTestProxy suite = createSuiteProxy();
    final RTestUnitTestProxy test = createTestProxy("test", suite);

    suite.setStarted();
    test.setTestFailed("", "", false);
    suite.setFinished();

    assertEquals("<UNKNOWN>", myColumnDuration.valueOf(suite));

    test.setDuration(10000);
    assertEquals(String.valueOf((float)10) + " s", myColumnDuration.valueOf(suite));
  }

  public void testValueOf_SuiteError() {
    final RTestUnitTestProxy suite = createSuiteProxy();
    final RTestUnitTestProxy test = createTestProxy("test", suite);

    suite.setStarted();
    test.setTestFailed("", "", true);
    suite.setFinished();

    assertEquals("<UNKNOWN>", myColumnDuration.valueOf(suite));

    test.setDuration(10000);
    assertEquals(String.valueOf((float)10) + " s", myColumnDuration.valueOf(suite));
  }

  public void testValueOf_SuitePassed() {
    final RTestUnitTestProxy suite = createSuiteProxy();
    final RTestUnitTestProxy test = createTestProxy("test", suite);

    suite.setStarted();
    test.setFinished();
    suite.setFinished();

    assertEquals("<UNKNOWN>", myColumnDuration.valueOf(suite));

    test.setDuration(10000);
    assertEquals(String.valueOf((float)10) + " s", myColumnDuration.valueOf(suite));
  }

  public void testValueOf_SuiteTerminated() {
    final RTestUnitTestProxy suite = createSuiteProxy();
    final RTestUnitTestProxy test = createTestProxy("test", suite);

    suite.setStarted();
    suite.setTerminated();

    assertEquals("<TERMINATED>", myColumnDuration.valueOf(suite));

    test.setDuration(10000);
    assertEquals("TERMINATED: " + String.valueOf((float)10) + " s", myColumnDuration.valueOf(suite));
  }

  public void testValueOf_SuiteRunning() {
    final RTestUnitTestProxy suite = createSuiteProxy();
    final RTestUnitTestProxy test = createTestProxy("test", suite);

    suite.setStarted();
    test.setStarted();

    assertEquals("<RUNNING>", myColumnDuration.valueOf(suite));

    test.setDuration(10000);
    assertEquals("RUNNING: " + String.valueOf((float)10) + " s", myColumnDuration.valueOf(suite));
  }
}
