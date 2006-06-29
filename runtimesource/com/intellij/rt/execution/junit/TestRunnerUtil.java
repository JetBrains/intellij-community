package com.intellij.rt.execution.junit;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.runner.BaseTestRunner;
import junit.textui.TestRunner;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.Vector;

public class TestRunnerUtil {
  /**
   * @noinspection HardCodedStringLiteral
   */
  private static ResourceBundle ourBundle = ResourceBundle.getBundle("RuntimeBundle");

  public static Test getTestSuite(IdeaTestRunner runner, String[] suiteClassNames){
    if (suiteClassNames.length == 0) {
      return null;
    }
    Vector result = new Vector();
    for (int i = 0; i < suiteClassNames.length; i++) {
      String suiteClassName = suiteClassNames[i];
      Test test;
      if (suiteClassName.charAt(0) == '@') {
        // all tests in the package specified
        String[] classNames;
        String suiteName;
        try {
          BufferedReader reader = new BufferedReader(new FileReader(suiteClassName.substring(1)));
          Vector vector;
          try {
            suiteName = reader.readLine();
            vector = new Vector();
            String line;
            while ((line = reader.readLine()) != null) {
              vector.addElement(line);
            }
          }
          finally {
            reader.close();
          }

          // toArray cannot be used here because the class must be compilable with 1.1
          classNames = new String[vector.size()];
          for (int j = 0; j < classNames.length; j++) {
            classNames[j] = (String)vector.elementAt(j);
          }
        }
        catch (Exception e) {
          runner.runFailed(MessageFormat.format(ourBundle.getString("junit.runner.error"), new Object[] {e.toString()}));
          return null;
        }
        test = new TestAllInPackage2(runner, suiteName, classNames);
      }
      else {
        test = createClassOrMethodSuite(runner, suiteClassName);
        if (test == null) return null;
      }
      result.addElement(test);
    }
    if (result.size() == 1) {
      return (Test)result.elementAt(0);
    }
    else {
      TestSuite suite = new TestSuite();
      for (int i = 0; i < result.size(); i++) {
        final Test test = (Test)result.elementAt(i);
        suite.addTest(test);
      }
      return suite;
    }
  }

  public static Test createClassOrMethodSuite(IdeaTestRunner runner, String suiteClassName) {
    String methodName = null;
    int index = suiteClassName.indexOf(',');
    if (index != -1) {
      methodName = suiteClassName.substring(index + 1);
      suiteClassName = suiteClassName.substring(0, index);
    }

    Class testClass = loadTestClass(runner, suiteClassName);
    if (testClass == null) return null;
    Test test = null;
    if (methodName == null) {
      if (runner.JUNIT4_API != null) {
        test = runner.JUNIT4_API.createClassSuite(testClass);
      }
      if (test == null) {
        try {
          Method suiteMethod = testClass.getMethod(BaseTestRunner.SUITE_METHODNAME, new Class[0]);
          if (!Modifier.isStatic(suiteMethod.getModifiers())) {
            String message = MessageFormat.format(ourBundle.getString("junit.suite.must.be.static"), new Object[]{testClass.getName()});
            System.err.println(message);
            //runFailed(message);
            return null;
          }
          try {
            test = (Test)suiteMethod.invoke(null, new Class[0]); // static method
          }
          catch (final InvocationTargetException e) {
            final String message = MessageFormat.format(ourBundle.getString("junit.failed.to.invoke.suite"), new Object[]{testClass + " " + e.getTargetException().toString()});
            //System.err.println(message);
            //runner.runFailed(message);
            runner.clearStatus();
            return new FailedTestCase(testClass, BaseTestRunner.SUITE_METHODNAME, message, e);
          }
          catch (IllegalAccessException e) {
            String message = MessageFormat.format(ourBundle.getString("junit.failed.to.invoke.suite"), new Object[]{testClass + " " + e.toString()});
            //System.err.println(message);
            //runner.runFailed(message);
            return new FailedTestCase(testClass, BaseTestRunner.SUITE_METHODNAME, message, e);
          }
        }
        catch (Exception e) {
          // try to extract a test suite automatically
          runner.clearStatus();
          test = new TestSuite(testClass);
        }
      }
    }
    else {
      test = createMethodSuite(runner, testClass, methodName);
    }
    return test;
  }

  private static Class loadTestClass(IdeaTestRunner runner, String suiteClassName) {
    try {
      return runner.loadSuiteClass(suiteClassName);
    }
    catch (ClassNotFoundException e) {
      String clazz = e.getMessage();
      if (clazz == null) {
        clazz = suiteClassName;
      }
      runner.runFailed(MessageFormat.format(ourBundle.getString("junit.class.not.found"), new Object[] {clazz}));
    }
    catch (Exception e) {
      runner.runFailed(MessageFormat.format(ourBundle.getString("junit.cannot.instantiate.tests"), new Object[] {e.toString()}));
    }
    return null;
  }

  private static Test createMethodSuite(IdeaTestRunner runner, Class testClass, String methodName) {
    runner.clearStatus();
    if (runner.JUNIT4_API != null) {
      Test test = runner.JUNIT4_API.createTestMethodSuite(testClass, methodName);
      if (test != null) return test;
    }
    try {
      Constructor constructor = testClass.getConstructor(new Class[]{String.class});
      TestCase test = (TestCase)constructor.newInstance(new Object[]{methodName});
      return test;
      //TestSuite testSuite = new TestSuite();
      //testSuite.addTest(test);
      //return testSuite;
    }
    catch (NoSuchMethodException e) {
      try {
        Constructor constructor = testClass.getConstructor(new Class[0]);
        TestCase test = (TestCase)constructor.newInstance(new Object[0]);
        test.setName(methodName);
        return test;
        //TestSuite testSuite = new TestSuite();
        //testSuite.addTest(test);
        //return testSuite;
      }
      catch(ClassCastException e1) {
        runner.runFailed(MessageFormat.format(ourBundle.getString("junit.class.not.derived"), new Object[] {testClass.getName()}));
        return null;
      }
      catch (Exception e1) {
        String message = MessageFormat.format(ourBundle.getString("junit.cannot.instantiate.tests"), new Object[]{e1.toString()});
        return new FailedTestCase(testClass, methodName, message, e1);
      }
    }
    catch (Exception e) {
      String message = MessageFormat.format(ourBundle.getString("junit.cannot.instantiate.tests"), new Object[]{e.toString()});
      return new FailedTestCase(testClass, methodName, message, e);
    }
  }

  private static void runFailed(String message) {
    System.err.println(message);
    System.exit(TestRunner.FAILURE_EXIT);
  }

  public static String testsFoundInPackageMesage(int testCount, String name) {
    return MessageFormat.format(ourBundle.getString("tests.found.in.package"), new Object[]{new Integer(testCount), name});
  }

  public static class FailedTestCase extends TestCase {
    private final String myMethodName;
    private final String myMessage;
    private final Throwable myThrowable;

    public FailedTestCase(final Class testClass, final String methodName, final String message, final Throwable e) {
      super(testClass.getName());
      myMethodName = methodName;
      myMessage = message;
      myThrowable = e;
    }

    public String getMethodName() {
      return myMethodName;
    }

    protected void runTest() throws Throwable {
      throw new RuntimeException(myMessage, myThrowable);
    }
  }
}
