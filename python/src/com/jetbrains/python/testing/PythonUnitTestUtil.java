package com.jetbrains.python.testing;

import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;

import java.util.ArrayList;
import java.util.List;

public class PythonUnitTestUtil {
  private static final String TESTCASE_CLASS_NAME = "TestCase";
  private static final String UNITTEST_FILE_NAME = "unittest.py";
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

      final String name = ancestor.getName();
      if (name == null || !name.equals(TESTCASE_CLASS_NAME)) {
        continue;
      }

      final PsiFile containingFile = ancestor.getContainingFile();
      if (!(containingFile instanceof PyFile) || !containingFile.getName().equals(UNITTEST_FILE_NAME)) {
        continue;
      }

      return true;
    }

    return false;
  }
}
