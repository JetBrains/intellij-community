package com.intellij.rt.execution.junit;

import com.intellij.rt.junit4.Junit4ClassSuite;
import junit.framework.Test;
import junit.framework.TestSuite;

import java.lang.reflect.Method;

/**
 * @noinspection HardCodedStringLiteral
 */
public class TestAllInPackage2 extends TestSuite {
  private final boolean isJunit4;

  public TestAllInPackage2(final String packageName, String[] classNames, final boolean is_junit4) {
    super(packageName);
    isJunit4 = is_junit4;

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

  private Test getTest(Class testClass) {
    if (isJunit4) {
      Junit4ClassSuite junit4Suite = new Junit4ClassSuite(testClass);
      if (junit4Suite.testCount() != 0) return junit4Suite;
    }
    try {
      Method suiteMethod = testClass.getMethod("suite", new Class[0]);
      Test test = (Test)suiteMethod.invoke(null, new Class[0]);
      return attachSuiteInfo(test, testClass);
    }
    catch (NoSuchMethodException e) {
      return new TestSuite(testClass);
    }
    catch (Exception e) {
      System.err.println("Failed to execute suite ()");
      e.printStackTrace();
    }
    return null;
  }

  private static Test attachSuiteInfo(Test test, Class testClass) {
    if (test instanceof TestSuite) {
      TestSuite testSuite = (TestSuite)test;
      if (testSuite.getName() == null)
        testSuite.setName(testClass.getName());
    }
    return test;
  }
}
