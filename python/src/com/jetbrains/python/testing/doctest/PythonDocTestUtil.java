package com.jetbrains.python.testing.doctest;

import com.google.common.collect.Lists;
import com.jetbrains.python.psi.*;

import java.util.List;
import java.util.StringTokenizer;

/**
 * User: catherine
 */
public class PythonDocTestUtil {

  private PythonDocTestUtil() {
  }

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
      PyStringLiteralExpression docString = file.getDocStringExpression();
      if (docString != null && hasExample(docString.getStringValue())) {
        result.add(file);
      }
    }
    return result;
  }

  public static boolean isDocTestFunction(PyFunction pyFunction) {
    if (pyFunction.getDocStringExpression() == null) return false;
    PyStringLiteralExpression docString = pyFunction.getDocStringExpression();
    if (docString != null && !hasExample(docString.getStringValue())) return false;
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
      PyStringLiteralExpression docString = pyClass.getDocStringExpression();
      if (docString != null && hasExample(docString.getStringValue())) {
        result.add(pyClass);
      }
    }
    if (result.isEmpty()) return false;
    return true; 
  }

  private static boolean hasExample(String docString) {
    boolean hasExample = false;
    StringTokenizer tokenizer = new StringTokenizer(docString, "\n");
    while (tokenizer.hasMoreTokens()) {
      String str = tokenizer.nextToken().trim();
      if (str.startsWith(">>>")) {
        hasExample = true;
        break;
      }
    }
    return hasExample;
  }
}