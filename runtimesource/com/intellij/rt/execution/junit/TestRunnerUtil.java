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

  public static Test getTestImpl(IdeaTestRunner runner, String suiteClassName){
    if (suiteClassName.length() <= 0) {
      runner.clearStatus();
      return null;
    }

    if (suiteClassName.startsWith("@")) {
      try {
        BufferedReader reader = new BufferedReader(new FileReader(suiteClassName.substring(1)));
        String packageName = reader.readLine();
        Vector vector = new Vector();
        String line;
        while ((line = reader.readLine()) != null) {
          vector.addElement(line);
        }
        reader.close();

        // toArray cannot be used here because the class must be compilable with 1.1


        String[] classNames = new String[vector.size()];
        for (int i = 0; i < classNames.length; i++) {
          classNames[i] = (String)vector.elementAt(i);
        }

        TestAllInPackage2 testPackage = new TestAllInPackage2(packageName, classNames);
        return testPackage;
      }
      catch (Exception e) {
        //noinspection HardCodedStringLiteral
        runner.runFailed(MessageFormat.format(ourBundle.getString("junit.runner.error"), new Object[] {e.toString()}));
        return null;
      }
    }

    String methodName = null;
    int index = suiteClassName.indexOf(',');
    if (index != -1) {
      methodName = suiteClassName.substring(index + 1);
      suiteClassName = suiteClassName.substring(0, index);
    }

    Class testClass;
    try {
      testClass = runner.loadSuiteClass(suiteClassName);
    }
    catch (ClassNotFoundException e) {
      String clazz = e.getMessage();
      if (clazz == null) {
        clazz = suiteClassName;
      }
      //noinspection HardCodedStringLiteral
      runner.runFailed(MessageFormat.format(ourBundle.getString("junit.class.not.found"), new Object[] {clazz}));
      return null;
    }
    catch (Exception e) {
      //noinspection HardCodedStringLiteral
      runner.runFailed(MessageFormat.format(ourBundle.getString("junit.cannot.instantiate.tests"), new Object[] {e.toString()}));
      return null;
    }

    if (methodName != null) {
      runner.clearStatus();

      try {
        Constructor constructor = testClass.getConstructor(new Class[]{String.class});
        TestCase test = (TestCase)constructor.newInstance(new Object[]{methodName});
        TestSuite testSuite = new TestSuite();
        testSuite.addTest(test);
        return testSuite;
      }
      catch (NoSuchMethodException e) {
        try {
          Constructor constructor = testClass.getConstructor(new Class[0]);
          TestCase test = (TestCase)constructor.newInstance(new Object[0]);
          test.setName(methodName);
          TestSuite testSuite = new TestSuite();
          testSuite.addTest(test);
          return testSuite;
        }
        catch(ClassCastException e1) {
          //noinspection HardCodedStringLiteral
          runner.runFailed(MessageFormat.format(ourBundle.getString("junit.class.not.derived"), new Object[] {testClass.getName()}));
          return null;
        }
        catch (Exception e1) {
          //noinspection HardCodedStringLiteral
          runner.runFailed(MessageFormat.format(ourBundle.getString("junit.cannot.instantiate.tests"), new Object[] {e1.toString()}));
          return null;
        }
      }
      catch (Exception e) {
        //noinspection HardCodedStringLiteral
        runner.runFailed(MessageFormat.format(ourBundle.getString("junit.cannot.instantiate.tests"), new Object[] {e.toString()}));
        return null;
      }
    }

    Method suiteMethod;
    try {
      suiteMethod = testClass.getMethod(BaseTestRunner.SUITE_METHODNAME, new Class[0]);
    }
    catch (Exception e) {
      // try to extract a test suite automatically
      runner.clearStatus();
      return new TestSuite(testClass);
    }
    if (! Modifier.isStatic(suiteMethod.getModifiers())) {
      //noinspection HardCodedStringLiteral
      runFailed(ourBundle.getString("junit.suite.must.be.static"));
      return null;
    }
    Test test;
    try {
      test = (Test)suiteMethod.invoke(null, new Class[0]); // static method
      if (test == null)
        return test;
    }
    catch (InvocationTargetException e) {
      //noinspection HardCodedStringLiteral
      runner.runFailed(MessageFormat.format(ourBundle.getString("junit.failed.to.invoke.suite"),
                                            new Object[] {e.getTargetException().toString()}));
      return null;
    }
    catch (IllegalAccessException e) {
      //noinspection HardCodedStringLiteral
      runner.runFailed(MessageFormat.format(ourBundle.getString("junit.failed.to.invoke.suite"),
                                            new Object[] {e.toString()}));
      return null;
    }

    runner.clearStatus();
    return test;
  }

  private static void runFailed(String message) {
    System.err.println(message);
    System.exit(TestRunner.FAILURE_EXIT);
  }
}
