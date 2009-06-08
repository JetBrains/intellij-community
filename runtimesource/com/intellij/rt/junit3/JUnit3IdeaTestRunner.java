package com.intellij.rt.junit3;

import com.intellij.rt.execution.junit.DeafStream;
import com.intellij.rt.execution.junit.IdeaTestRunner;
import com.intellij.rt.execution.junit.segments.PoolOfDelimiters;
import com.intellij.rt.execution.junit.segments.SegmentedOutputStream;
import junit.framework.*;
import junit.textui.ResultPrinter;
import junit.textui.TestRunner;

public class JUnit3IdeaTestRunner extends TestRunner implements IdeaTestRunner {
  private TestListener myTestsListener;
  private JUnit3OutputObjectRegistry myRegistry;

  public JUnit3IdeaTestRunner() {
    super(DeafStream.DEAF_PRINT_STREAM);
  }

  public int startRunnerWithArgs(String[] args) {
    try {
      Test suite = TestRunnerUtil.getTestSuite(this, args);
      TestResult result = doRun(suite);
      if (!result.wasSuccessful()) {
        return -1;
      }
      return 0;
    }
    catch (Exception e) {
      e.printStackTrace(System.err);
      return -2;
    }
  }

  public void clearStatus() {
    super.clearStatus();
  }

  public void runFailed(String message) {
    super.runFailed(message);
  }

  public void setStreams(SegmentedOutputStream segmentedOut, SegmentedOutputStream segmentedErr) {
    setPrinter(new TimeSender());
    myRegistry = new JUnit3OutputObjectRegistry(segmentedOut, segmentedErr);
    myTestsListener = new TestResultsSender(myRegistry, segmentedErr);
  }

  protected TestResult createTestResult() {
    TestResult testResult = super.createTestResult();
    testResult.addListener(myTestsListener);
    try {
      final Object data = Class.forName("com.intellij.rt.coverage.data.ProjectData").getMethod("getProjectData", new Class[0]).invoke(null, new Object[0]);
      if (data != null) {
        testResult.addListener(new TestListener() {
          public void addError(final Test test, final Throwable t) {}
          public void addFailure(final Test test, final AssertionFailedError t) {}

          public void startTest(final Test test) {
            if (test instanceof TestCase) {
              ((com.intellij.rt.coverage.data.ProjectData)data).testStarted(test.getClass().getName() + "." + ((TestCase)test).getName());
            }
          }

          public void endTest(final Test test) {
            if (test instanceof TestCase) {
              ((com.intellij.rt.coverage.data.ProjectData)data).testEnded(test.getClass().getName() + "." + ((TestCase)test).getName());
            }
          }
        });
      }
    }
    catch (Throwable e) {
      //coverage was not enabled
    }

    return testResult;
  }

  public TestResult doRun(Test suite, boolean wait) {
    try {
      TreeSender.sendSuite(myRegistry, suite);
    }
    catch (Exception e) {
      //noinspection HardCodedStringLiteral
      System.err.println("Internal Error occured.");
      e.printStackTrace(System.err);
    }
    return super.doRun(suite, wait);
  }

  public static class MockResultPrinter extends ResultPrinter {
    public MockResultPrinter() {
      super(DeafStream.DEAF_PRINT_STREAM);
    }
  }

  private class TimeSender extends ResultPrinter {
    public TimeSender() {
      super(DeafStream.DEAF_PRINT_STREAM);
    }

    protected void printHeader(long runTime) {
      myRegistry.createPacket().addString(PoolOfDelimiters.TESTS_DONE).addLong(runTime).send();
    }
  }
}
