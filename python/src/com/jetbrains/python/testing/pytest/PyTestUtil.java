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
package com.jetbrains.python.testing.pytest;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * User: catherine
 */
public class PyTestUtil {
  private static final Set<String> PYTHON_TEST_QUALIFIED_CLASSES = ImmutableSet.of("unittest.TestCase", "unittest.case.TestCase");

  static List<PyStatement> getPyTestCasesFromFile(PsiFileSystemItem file, @NotNull final TypeEvalContext context) {
    List<PyStatement> result = Lists.newArrayList();
    if (file instanceof PyFile) {
      result = getResult((PyFile)file, context);
    }
    else if (file instanceof PsiDirectory) {
      for (PsiFile f : ((PsiDirectory)file).getFiles()) {
        if (f instanceof PyFile) {
          result.addAll(getResult((PyFile)f, context));
        }
      }
    }
    return result;
  }

  private static List<PyStatement> getResult(PyFile file, @NotNull final TypeEvalContext context) {
    List<PyStatement> result = Lists.newArrayList();
    for (PyClass cls : file.getTopLevelClasses()) {
      if (isPyTestClass(cls, context)) {
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

  public static boolean isPyTestClass(final PyClass pyClass, @Nullable final TypeEvalContext context) {
    final TypeEvalContext contextToUse = (context != null ? context : TypeEvalContext.codeInsightFallback(pyClass.getProject()));
    for (PyClassLikeType type : pyClass.getAncestorTypes(contextToUse)) {
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
