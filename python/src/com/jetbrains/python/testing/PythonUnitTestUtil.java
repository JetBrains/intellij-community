package com.jetbrains.python.testing;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestUtil {
  public static final String TESTCASE_SETUP_NAME = "setUp";
  private static final String TESTCASE_METHOD_PREFIX = "test";

  private PythonUnitTestUtil() {
  }

  public static List<PyClass> getTestCaseClassesFromFile(PyFile file) {
    List<PyClass> result = new ArrayList<PyClass>();
    for (PyClass cls : file.getTopLevelClasses()) {
      if (isTestCaseClass(cls)) {
        result.add(cls);
      }
    }
    return result;
  }

  public static boolean isTestCaseFunction(PyFunction function) {
    final String name = function.getName();
    if (name == null || !name.startsWith(TESTCASE_METHOD_PREFIX)) {
      return false;
    }

    final PyClass containingClass = function.getContainingClass();
    if (containingClass == null || !isTestCaseClass(containingClass)) {
      return false;
    }

    return true;
  }

  public static boolean isTestCaseClass(PyClass cls) {
    for (PyClass ancestor : cls.iterateAncestors()) {
      if (ancestor == null) continue;

      String qName = ancestor.getQualifiedName();
      if ("unittest.TestCase".equals(qName) || "unittest.case.TestCase".equals(qName)) {
        return true;
      }
    }

    return false;
  }
}
