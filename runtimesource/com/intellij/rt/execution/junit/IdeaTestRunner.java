package com.intellij.rt.execution.junit;

public interface IdeaTestRunner {
  void clearStatus();

  Class loadSuiteClass(String suiteClassName) throws ClassNotFoundException;

  void runFailed(String message);
}
