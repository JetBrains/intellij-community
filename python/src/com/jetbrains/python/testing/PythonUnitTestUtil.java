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

import com.google.common.collect.Sets;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ThreeState;
import com.jetbrains.extensions.PyClassExtKt;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;


/**
 * Tools to check if some file, function or class could be test case.
 * There are 2 strategies: "testCaseClassRequired" means only TesCase inheritors are considered as test cases.
 * In opposite case any function named "test*" either top level or situated in class named Test* or *Test is test case.
 * Providing null to "testCaseClassRequired" means "use default runner settings" and usually best value.
 *
 * @author Leonid Shalupov
 * @author Ilya.Kazakevich
 */
public final class PythonUnitTestUtil {
  public static final String TESTCASE_SETUP_NAME = "setUp";
  public static final Set<String> PYTHON_TEST_QUALIFIED_CLASSES = Collections.unmodifiableSet(Sets.newHashSet("unittest.TestCase",
                                                                                                              "unittest.case.TestCase"));

  private PythonUnitTestUtil() {
  }


  public static boolean isTestFile(@NotNull final PyFile file,
                                   @NotNull final ThreeState testCaseClassRequired,
                                   @Nullable final TypeEvalContext context) {
    if (file.getTopLevelClasses().stream().anyMatch(o -> isTestClass(o, testCaseClassRequired, context))) {
      return true;
    }

    if (isTestCaseClassRequired(file, testCaseClassRequired)) {
      return false;
    }
    return file.getTopLevelFunctions().stream().anyMatch(o -> isTestFunction(o, testCaseClassRequired, context));
  }



  public static boolean isTestClass(@NotNull final PyClass cls,
                                    @NotNull final ThreeState testCaseClassRequired,
                                    @Nullable TypeEvalContext context) {
    final boolean testCaseOnly = isTestCaseClassRequired(cls, testCaseClassRequired);
    if (context == null) {
      context = TypeEvalContext.codeInsightFallback(cls.getProject());
    }
    final boolean inheritsTestCase = PyClassExtKt.inherits(cls, context, PYTHON_TEST_QUALIFIED_CLASSES);
    if (inheritsTestCase) {
      return true;
    }

    if (testCaseOnly) {
      return false;
    }

    final String className = cls.getName();
    if (className == null) {
      return false;
    }

    if (!className.startsWith("Test") && !className.endsWith("Test")) {
      return false;
    }

    Ref<Boolean> result = new Ref<>(false);
    cls.visitMethods(function -> {
      final String name = function.getName();
      if (name != null && name.startsWith("test")) {
        result.set(true);
        return false;
      }
      return true;
    }, true, context);
    return result.get();
  }


  public static boolean isTestFunction(@NotNull final PyFunction function,
                                       @NotNull final ThreeState testCaseClassRequired,
                                       @Nullable final TypeEvalContext context) {
    final String name = function.getName();
    if (name == null || !name.startsWith("test")) {
      // Since there are a lot of ways to launch assert in modern frameworks,
      // we assume any function with "test" word in name is test
      return false;
    }
    // If testcase not required then any test function is test
    final PyClass aClass = function.getContainingClass();
    if (!isTestCaseClassRequired(function, testCaseClassRequired) && aClass == null) {
      return true;
    }
    return aClass != null && isTestClass(aClass, testCaseClassRequired, context);
  }


  /**
   * @deprecated Use {@link #isTestClass(PyClass, ThreeState, TypeEvalContext)} instead.
   * Will be removed in 2018.
   */
  @Deprecated
  public static boolean isUnitTestCaseClass(PyClass cls) {
    return isTestClass(cls, ThreeState.YES, null);
  }


  private static boolean isTestCaseClassRequired(@NotNull final PsiElement anchor, @NotNull final ThreeState userProvidedValue) {
    if (userProvidedValue != ThreeState.UNSURE) {
      return userProvidedValue.toBoolean();
    }
    final Module module = ModuleUtilCore.findModuleForPsiElement(anchor);
    if (module == null) {
      return true;
    }
    return PyTestsSharedKt.getRunnersThatRequireTestCaseClass().contains(TestRunnerService.getInstance(module).getProjectConfiguration());
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
