package com.jetbrains.python.testing;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestUtil {
  public static final String TESTCASE_SETUP_NAME = "setUp";
  private static final String TESTCASE_METHOD_PREFIX = "test";
  private static final HashSet<String> PYTHON_TEST_QUALIFIED_CLASSES = Sets.newHashSet("unittest.TestCase", "unittest.case.TestCase");

  private PythonUnitTestUtil() {
  }

  public static List<PyClass> getTestCaseClassesFromFile(PyFile file) {
    return getTestCaseClassesFromFile(file, PYTHON_TEST_QUALIFIED_CLASSES);
  }

  public static List<PyClass> getTestCaseClassesFromFile(PyFile file, Set<String> testQualifiedNames) {
    List<PyClass> result = Lists.newArrayList();
    for (PyClass cls : file.getTopLevelClasses()) {
      if (isTestCaseClass(cls, testQualifiedNames)) {
        result.add(cls);
      }
    }
    return result;
  }

  public static boolean isTestCaseFunction(PyFunction function) {
    return isTestCaseFunction(function, PYTHON_TEST_QUALIFIED_CLASSES);

  }

  public static boolean isTestCaseFunction(PyFunction function, Set<String> testQualifiedNames) {
    final String name = function.getName();
    if (name == null || !name.startsWith(TESTCASE_METHOD_PREFIX)) {
      return false;
    }

    final PyClass containingClass = function.getContainingClass();
    if (containingClass == null || !isTestCaseClass(containingClass, testQualifiedNames)) {
      return false;
    }

    return true;
  }

  public static boolean isTestCaseClass(@NotNull PyClass cls) {
    return isTestCaseClass(cls, PYTHON_TEST_QUALIFIED_CLASSES);
  }

  public static boolean isTestCaseClass(@NotNull PyClass cls, Set<String> testQualifiedNames) {
    for (PyClass ancestor : cls.iterateAncestors()) {
      if (ancestor == null) continue;

      String qName = ancestor.getQualifiedName();
      if (testQualifiedNames.contains(qName)) {
        return true;
      }
    }

    return false;
  }
}
