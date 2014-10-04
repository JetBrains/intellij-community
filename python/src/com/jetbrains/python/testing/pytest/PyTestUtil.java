/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.testing.pytest;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;

import java.util.HashSet;
import java.util.List;

/**
 * User: catherine
 */
public class PyTestUtil {
  private static final HashSet<String> PYTHON_TEST_QUALIFIED_CLASSES = Sets.newHashSet("unittest.TestCase", "unittest.case.TestCase");

  public static List<PyStatement> getPyTestCasesFromFile(PsiFileSystemItem file) {
    List<PyStatement> result = Lists.newArrayList();
    if (file instanceof PyFile) {
      result = getResult((PyFile)file);
    }
    else if (file instanceof PsiDirectory) {
      for (PsiFile f : ((PsiDirectory)file).getFiles()) {
        if (f instanceof PyFile)
          result.addAll(getResult((PyFile)f));
      }
    }
    return result;
  }

  private static List<PyStatement> getResult(PyFile file) {
    List<PyStatement> result = Lists.newArrayList();
    for (PyClass cls : file.getTopLevelClasses()) {
      if (isPyTestClass(cls)) {
        result.add(cls);
      }
    }
    for (PyFunction cls : file.getTopLevelFunctions()) {
      if (isPyTestFunction(cls)) {
        result.add(cls);
      }
    }
    return result;
  }

  public static boolean isPyTestFunction(PyFunction pyFunction) {
    String name = pyFunction.getName();
    if (name != null && name.startsWith("test")) {
      return true;
    }
    return false;
  }

  public static boolean isPyTestClass(PyClass pyClass) {
    for (PyClassLikeType type : pyClass.getAncestorTypes(TypeEvalContext.codeInsightFallback())) {
      if (type != null && PYTHON_TEST_QUALIFIED_CLASSES.contains(type.getClassQName())) {
        return true;
      }
    }
    final String className = pyClass.getName();
    if (className == null) return false;
    final String name = className.toLowerCase();
    if (name.startsWith("test")) {
      for (PyFunction cls : pyClass.getMethods()) {
        if (isPyTestFunction(cls)) {
          return true;
        }
      }
    }
    return false;
  }
}
