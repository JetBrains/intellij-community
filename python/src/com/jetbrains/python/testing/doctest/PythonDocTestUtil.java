/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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