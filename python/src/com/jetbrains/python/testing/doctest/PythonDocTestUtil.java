// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.doctest;

import com.jetbrains.python.psi.*;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public final class PythonDocTestUtil {

  private PythonDocTestUtil() {
  }

  public static List<PyElement> getDocTestCasesFromFile(PyFile file) {
    List<PyElement> result = new ArrayList<>();
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
    for (PyFunction cls : pyClass.getMethods()) {
      if (isDocTestFunction(cls)) {
        return true;
      }
    }
    if (pyClass.getDocStringExpression() != null) {
      PyStringLiteralExpression docString = pyClass.getDocStringExpression();
      if (docString != null && hasExample(docString.getStringValue())) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasExample(final String docString) {
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