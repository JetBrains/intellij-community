package com.intellij.rt.execution.junit;

import junit.framework.Test;
import junit.framework.TestSuite;

import java.lang.reflect.Method;

public class TestAllInPackage2 extends TestSuite {

  public TestAllInPackage2(final String packageName, String[] classNames) {
    super(packageName);

    int testClassCount = 0;

    for (int i = 0; i < classNames.length; i++) {
      String className = classNames[i];
      try {
        final Class candidateClass = Class.forName(className);
        Test test = getTest(candidateClass);
        if (test != null) {
          addTest(test);
          testClassCount++;
        }
      }
      catch (ClassNotFoundException e) {
        System.err.println("Cannot load class: " + className + " " + e.getMessage());
      }
      catch (NoClassDefFoundError e) {
        System.err.println("Cannot load class that " + className + " is dependant on");
      }
      catch (ExceptionInInitializerError e) {
        e.getException().printStackTrace();
        System.err.println("Cannot load class: " + className + " " + e.getException().getMessage());
      }
    }

    String classString = testClassCount == 1 ? "class" : "classes";
    System.out.println(Integer.toString(testClassCount) +  " test "+ classString + " found in package \"" + packageName + "\"\n");
  }

  private Test getTest(Class testCaseClass) {
    try {
      Method suiteMethod = testCaseClass.getMethod("suite", new Class[0]);
      Test test = (Test)suiteMethod.invoke(null, new Class[0]);
      return attachSuiteInfo(test, testCaseClass);
    } catch (NoSuchMethodException e) {
      return new TestSuite(testCaseClass);
    } catch (Exception e) {
      System.err.println("Failed to execute suite ()");
      e.printStackTrace();
    }
    return null;
  }

  private Test attachSuiteInfo(Test test, Class testClass) {
    if (test instanceof TestSuite) {
      TestSuite testSuite = (TestSuite)test;
      if (testSuite.getName() == null)
        testSuite.setName(testClass.getName());
    }
    return test;
  }
}
