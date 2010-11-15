package com.jetbrains.python.testing.doctest;

import com.google.common.collect.Lists;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatement;

import java.util.List;
import java.util.Set;

/**
 * User: catherine
 */
public class PythonDocTestUtil {

  public static List<PyStatement> getDocTestCasesFromFile(PyFile file) {
    List<PyStatement> result = Lists.newArrayList();
    for (PyClass cls : file.getTopLevelClasses()) {
      if (isDocTestClass(cls)) {
        result.add(cls);
      }
    }
    for (PyFunction cls : file.getTopLevelFunctions()) {
      if (isDocTestFunction(cls)) {
        result.add(cls);
      }
    }
    return result;
  }

  public static boolean isDocTestFunction(PyFunction pyFunction) {
    if (pyFunction.getDocStringExpression() == null) return false;
    PythonDocStringParser parser = new PythonDocStringParser(pyFunction.getDocStringExpression().getStringValue());
    if (!parser.hasExample()) return false;
    return true;
  }

  public static boolean isDocTestClass(PyClass pyClass) {
    List<PyStatement> result = Lists.newArrayList();
    for (PyFunction cls : pyClass.getMethods()) {
      if (isDocTestFunction(cls)) {
        result.add(cls);
      }
    }
    if (result.isEmpty()) return false;
    return true; 
  }
}
