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
package com.jetbrains.python.testing;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;


/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestUtil {
  public static final String TESTCASE_SETUP_NAME = "setUp";
  private static final HashSet<String> PYTHON_TEST_QUALIFIED_CLASSES = Sets.newHashSet("unittest.TestCase", "unittest.case.TestCase");
  private static final Pattern TEST_MATCH_PATTERN = Pattern.compile("(?:^|[\b_\\.%s-])[Tt]est");
  private static final String TESTCASE_METHOD_PREFIX = "test";

  private PythonUnitTestUtil() {
  }

  public static boolean isUnitTestCaseFunction(PyFunction function) {
    final String name = function.getName();
    if (name == null || !name.startsWith(TESTCASE_METHOD_PREFIX)) {
      return false;
    }

    final PyClass containingClass = function.getContainingClass();
    if (containingClass == null || !isUnitTestCaseClass(containingClass, PYTHON_TEST_QUALIFIED_CLASSES)) {
      return false;
    }

    return true;
  }

  public static boolean isUnitTestCaseClass(PyClass cls) {
    return isUnitTestCaseClass(cls, PYTHON_TEST_QUALIFIED_CLASSES);
  }

  public static boolean isUnitTestFile(PyFile file) {
    if (!file.getName().startsWith("test")) return false;
    return true;
  }

  private static boolean isUnitTestCaseClass(PyClass cls, HashSet<String> testQualifiedNames) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      for (PyExpression expression : cls.getSuperClassExpressions()) {
        if (expression.getText().equals("TestCase")) return true;
      }
    }
    for (PyClassLikeType type : cls.getAncestorTypes(TypeEvalContext.codeInsightFallback(cls.getProject()))) {
      if (type != null && testQualifiedNames.contains(type.getClassQName())) {
        return true;
      }
    }
    return false;
  }

  public static List<PyStatement> getTestCaseClassesFromFile(PsiFile file, @Nullable final TypeEvalContext context) {
    if (file instanceof PyFile) {
      return getTestCaseClassesFromFile((PyFile)file, PYTHON_TEST_QUALIFIED_CLASSES, context);
    }
    return Collections.emptyList();
  }

  public static List<PyStatement> getTestCaseClassesFromFile(PyFile file, Set<String> testQualifiedNames, @Nullable final TypeEvalContext context) {
    List<PyStatement> result = Lists.newArrayList();
    for (PyClass cls : file.getTopLevelClasses()) {
      if (isTestCaseClassWithContext(cls, testQualifiedNames, context)) {
        result.add(cls);
      }
    }
    for (PyFunction cls : file.getTopLevelFunctions()) {
      if (isTestCaseFunction(cls, false)) {
        result.add(cls);
      }
    }
    return result;
  }

  public static boolean isTestCaseFunction(PyFunction function) {
    return isTestCaseFunction(function, true);
  }

  public static boolean isTestCaseFunction(PyFunction function, boolean checkAssert) {
    final String name = function.getName();
    if (name == null || !TEST_MATCH_PATTERN.matcher(name).find()) {
      return false;
    }
    if (function.getContainingClass() != null) {
      if (isTestCaseClass(function.getContainingClass(), null)) return true;
    }
    if (checkAssert) {
      boolean hasAssert = hasAssertOrYield(function.getStatementList());
      if (hasAssert) return true;
    }
    return false;
  }

  private static boolean hasAssertOrYield(PyStatementList list) {
    Stack<PsiElement> stack = new Stack<>();
    if (list != null) {
      for (PyStatement st : list.getStatements()) {
        stack.push(st);
        while (!stack.isEmpty()) {
          PsiElement e = stack.pop();
          if (e instanceof PyAssertStatement || e instanceof PyYieldExpression) return true;
          for (PsiElement psiElement : e.getChildren()) {
            stack.push(psiElement);
          }
        }
      }
    }
    return false;
  }

  public static boolean isTestCaseClass(@NotNull PyClass cls, @Nullable final TypeEvalContext context) {
    return isTestCaseClassWithContext(cls, PYTHON_TEST_QUALIFIED_CLASSES, context);
  }

  public static boolean isTestCaseClassWithContext(@NotNull PyClass cls,
                                                   Set<String> testQualifiedNames,
                                                   @Nullable TypeEvalContext context) {
    final TypeEvalContext contextToUse = (context != null ? context : TypeEvalContext.codeInsightFallback(cls.getProject()));
    for (PyClassLikeType type : cls.getAncestorTypes(contextToUse)) {
      if (type != null) {
        if (testQualifiedNames.contains(type.getClassQName())) {
          return true;
        }
        String clsName = cls.getQualifiedName();
        String[] names = new String[0];
        if (clsName != null) {
          names = clsName.split("\\.");
        }
        if (names.length == 0) return false;

        clsName = names[names.length - 1];
        if (TEST_MATCH_PATTERN.matcher(clsName).find()) {
          return true;
        }
      }
    }
    return false;
  }

  public static List<Location> findLocations(@NotNull final Project project,
                                             @NotNull String fileName,
                                             @Nullable String className,
                                             @Nullable String methodName) {
    if (fileName.contains("%")) {
      fileName = fileName.substring(0, fileName.lastIndexOf("%"));
    }
    final List<Location> locations = new ArrayList<>();
    if (methodName == null && className == null) {
      final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(fileName);
      if (virtualFile == null) return locations;
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      if (psiFile != null) {
        locations.add(new PsiLocation<>(project, psiFile));
      }
    }

    if (className != null) {
      for (PyClass cls : PyClassNameIndex.find(className, project, false)) {
        ProgressManager.checkCanceled();

        final PsiFile containingFile = cls.getContainingFile();
        final VirtualFile virtualFile = containingFile.getVirtualFile();
        final String clsFileName = virtualFile == null ? containingFile.getName() : virtualFile.getPath();
        final String clsFileNameWithoutExt = FileUtil.getNameWithoutExtension(clsFileName);
        if (!clsFileNameWithoutExt.endsWith(fileName) && !fileName.equals(clsFileName)) {
          continue;
        }
        if (methodName == null) {
          locations.add(new PsiLocation<>(project, cls));
        }
        else {
          final PyFunction method = cls.findMethodByName(methodName, true, null);
          if (method == null) {
            continue;
          }

          locations.add(new PyPsiLocationWithFixedClass(project, method, cls));
        }
      }
    }
    else if (methodName != null) {
      for (PyFunction function : PyFunctionNameIndex.find(methodName, project)) {
        ProgressManager.checkCanceled();
        if (function.getContainingClass() == null) {
          final PsiFile containingFile = function.getContainingFile();
          final VirtualFile virtualFile = containingFile.getVirtualFile();
          final String clsFileName = virtualFile == null ? containingFile.getName() : virtualFile.getPath();
          final String clsFileNameWithoutExt = FileUtil.getNameWithoutExtension(clsFileName);
          if (!clsFileNameWithoutExt.endsWith(fileName)) {
            continue;
          }
          locations.add(new PsiLocation<>(project, function));
        }
      }
    }
    return locations;
  }
}
