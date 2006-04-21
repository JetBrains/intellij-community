package com.intellij.rt.execution.junit;

import junit.framework.Test;
import junit.framework.TestSuite;

public class TestAllInPackage2 extends TestSuite {
  public TestAllInPackage2(IdeaTestRunner runner, final String name, String[] classMethodNames) {
    super(name);
    int testClassCount = 0;

    for (int i = 0; i < classMethodNames.length; i++) {
      String classMethodName = classMethodNames[i];
      Test suite = TestRunnerUtil.createClassOrMethodSuite(runner, classMethodName);
      if (suite != null) {
        if (suite instanceof TestSuite && ((TestSuite)suite).getName() == null) {
          attachSuiteInfo(suite, classMethodName);
        }
        addTest(suite);
        testClassCount++;
      }
    }
    String message = TestRunnerUtil.testsFoundInPackageMesage(testClassCount, name);
    System.out.println(message);
  }

  private static Test attachSuiteInfo(Test test, String name) {
    if (test instanceof TestSuite) {
      TestSuite testSuite = (TestSuite)test;
      if (testSuite.getName() == null)
        testSuite.setName(name);
    }
    return test;
  }
}
