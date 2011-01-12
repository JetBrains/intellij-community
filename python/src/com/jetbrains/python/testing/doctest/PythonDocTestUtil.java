package com.jetbrains.python.testing.doctest;

import com.google.common.collect.Lists;
import com.jetbrains.python.psi.*;

import java.util.List;

/**
 * User: catherine
 */
public class PythonDocTestUtil {

  public static List<PyElement> getDocTestCasesFromFile(PyFile file) {
    List<PyElement> result = Lists.newArrayList();
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
    if (file.getDocStringExpression() != null) {
      PythonDocStringParser parser = new PythonDocStringParser(file.getDocStringExpression().getStringValue());
      if (!parser.hasExample()) {
        result.add(file);
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
    if (pyClass.getDocStringExpression() != null) {
      PythonDocStringParser parser = new PythonDocStringParser(pyClass.getDocStringExpression().getStringValue());
      if (parser.hasExample()) {
        result.add(pyClass);
      }
    }
    if (result.isEmpty()) return false;
    return true; 
  }
}
