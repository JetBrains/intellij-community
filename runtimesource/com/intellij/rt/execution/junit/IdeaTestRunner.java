package com.intellij.rt.execution.junit;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.textui.TestRunner;

import java.io.PrintStream;

public class IdeaTestRunner extends TestRunner {
  public IdeaTestRunner(PrintStream writer) {
    super(writer);
  }

  public static int startRunnerWithArgs(IdeaTestRunner testRunner, String[] args) {
    try {
      TestResult result = testRunner.start(args);
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

  public Test getTest(String suiteClassName) {
    return TestRunnerUtil.getTestImpl(this, suiteClassName);
  }

  public void clearStatus() {
    super.clearStatus();
  }

  public Class loadSuiteClass(String suiteClassName) throws ClassNotFoundException {
    return super.loadSuiteClass(suiteClassName);
  }

  public void runFailed(String message) {
    super.runFailed(message);
  }
}
