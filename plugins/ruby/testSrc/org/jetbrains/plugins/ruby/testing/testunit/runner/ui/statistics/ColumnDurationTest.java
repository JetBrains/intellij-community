package org.jetbrains.plugins.ruby.testing.testunit.runner.ui.statistics;

import org.jetbrains.plugins.ruby.testing.testunit.runner.BaseRUnitTestsTestCase;

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
    assertEquals("<unknown>", myColumnDuration.valueOf(mySimpleTest));
  }

  public void testValueOf_InProgress() {
    mySimpleTest.setStarted();
    assertEquals("<unknown>", myColumnDuration.valueOf(mySimpleTest));
  }

  public void testValueOf_TestFailure() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", false);
    assertEquals("<unknown>", myColumnDuration.valueOf(mySimpleTest));
  }

  public void testValueOf_TestPassed() {
    mySimpleTest.setStarted();
    mySimpleTest.setFinished();
    assertEquals("<unknown>", myColumnDuration.valueOf(mySimpleTest));
  }

  public void testValueOf_TestError() {
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("", "", true);
    assertEquals("<unknown>", myColumnDuration.valueOf(mySimpleTest));
  }

  public void testValueOf_Terminated() {
    mySimpleTest.setStarted();
    mySimpleTest.setTerminated();
    assertEquals("<unknown>", myColumnDuration.valueOf(mySimpleTest));
  }
}
