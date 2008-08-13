package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import org.jetbrains.plugins.ruby.testing.testunit.runner.BaseRUnitTestsTestCase;

/**
 * @author Roman Chernyatchik
 */
public class ColumnResultsTests extends BaseRUnitTestsTestCase {
  private ColumnResults myColumnResults;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myColumnResults = new ColumnResults();
    //TODO test cell renderer!
  }

  public void testValueOf_TestNotRun() {
    assertEquals("Not run", myColumnResults.valueOf(mySimpleTest));
  }

  public void testValueOf_TestInProgress() {
    mySimpleTest.setStarted();
    assertEquals("Running...", myColumnResults.valueOf(mySimpleTest));
  }

  public void testValueOf_TestFailure() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    assertEquals("Assertion failed", myColumnResults.valueOf(mySimpleTest));
  }

  public void testValueOf_TestPassed() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    assertEquals("Passed", myColumnResults.valueOf(mySimpleTest));
  }

  public void testValueOf_TestError() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);
    assertEquals("Error", myColumnResults.valueOf(mySimpleTest));
  }

  public void testValueOf_TestTerminated() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();
    assertEquals("Terminated", myColumnResults.valueOf(mySimpleTest));
  }
}
