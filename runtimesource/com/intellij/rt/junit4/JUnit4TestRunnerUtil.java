package com.intellij.rt.junit4;

import org.junit.runner.Computer;
import org.junit.runner.Description;
import org.junit.runner.Request;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

public class JUnit4TestRunnerUtil {
  /**
   * @noinspection HardCodedStringLiteral
   */
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("RuntimeBundle");

  public static Request buildRequest(String[] suiteClassNames) {
    if (suiteClassNames.length == 0) {
      return null;
    }
    Vector result = new Vector();
    for (int i = 0; i < suiteClassNames.length; i++) {
      String suiteClassName = suiteClassNames[i];
      if (suiteClassName.charAt(0) == '@') {
        // all tests in the package specified
        try {
          final Map classMethods = new HashMap();
          BufferedReader reader = new BufferedReader(new FileReader(suiteClassName.substring(1)));
          try {
            final String packageName = reader.readLine();
            String line;

            while ((line = reader.readLine()) != null) {
              String className = line;
              final int idx = line.indexOf(',');
              if (idx != -1) {
                className = line.substring(0, idx);
                Set methodNames = (Set)classMethods.get(className);
                if (methodNames == null) {
                  methodNames = new HashSet();
                  classMethods.put(className, methodNames);
                }
                methodNames.add(line.substring(idx + 1));

              }
              result.addElement(loadTestClass(className));
            }

            final Request allClasses = Request.classes(new IdeaComputer(packageName), getArrayOfClasses(result));
            return classMethods.isEmpty() ? allClasses : allClasses.filterWith(new Filter() {
              public boolean shouldRun(Description description) {
                if (description.isTest()) {
                  final Set methods = (Set)classMethods.get(description.getClassName());
                  return methods == null || methods.contains(description.getMethodName());
                }
                return true;
              }

              public String describe() {
                return "Failed tests";
              }
            });
          }
          finally {
            reader.close();
          }
        }
        catch (IOException e) {
          e.printStackTrace();
          System.exit(1);
        }
      }
      else {
        int index = suiteClassName.indexOf(',');
        if (index != -1) {
          return Request.method(loadTestClass(suiteClassName.substring(0, index)), suiteClassName.substring(index + 1));
        }
        result.addElement(loadTestClass(suiteClassName));
      }
    }

    return result.size() == 1 ? Request.aClass((Class)result.get(0)) : Request.classes(getArrayOfClasses(result));
  }

  private static Class[] getArrayOfClasses(Vector result) {
    Class[] classes = new Class[result.size()];
    for (int i = 0; i < result.size(); i++) {
      classes[i] = (Class)result.get(i);
    }
    return classes;
  }

  private static Class loadTestClass(String suiteClassName) {
    try {
      return Class.forName(suiteClassName);
    }
    catch (ClassNotFoundException e) {
      String clazz = e.getMessage();
      if (clazz == null) {
        clazz = suiteClassName;
      }
      System.err.print(MessageFormat.format(ourBundle.getString("junit.class.not.found"), new Object[]{clazz}));
      System.exit(1);
    }
    catch (Exception e) {
      System.err.println(MessageFormat.format(ourBundle.getString("junit.cannot.instantiate.tests"), new Object[]{e.toString()}));
      System.exit(1);
    }
    return null;
  }

  public static String testsFoundInPackageMesage(int testCount, String name) {
    return MessageFormat.format(ourBundle.getString("tests.found.in.package"), new Object[]{new Integer(testCount), name});
  }


  private static class IdeaComputer extends Computer {
    private String myName;

    public IdeaComputer(String name) {
      myName = name;
    }

    public Suite getSuite(final RunnerBuilder builder, Class[] classes) throws InitializationError {
      return new Suite(new RunnerBuilder() {
        public Runner runnerForClass(Class testClass) throws Throwable {
          return getRunner(builder, testClass);
        }
      }, classes) {
        public Description getDescription() {
          Description description = Description.createSuiteDescription(myName, getTestClass().getAnnotations());
          List filteredChildren = getFilteredChildren();
          for (int i = 0, filteredChildrenSize = filteredChildren.size(); i < filteredChildrenSize; i++) {
            Object child = filteredChildren.get(i);
            description.addChild(describeChild((Runner)child));
          }
          return description;
        }
      };
    }
  }
}