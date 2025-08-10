// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ThreeState;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.testing.PythonUnitTestDetectorsKt.isUnitTestCaseClass;


/**
 * Tools to check if some file, function or class could be test case.
 * There are 2 strategies: "testCaseClassRequired" means only TesCase inheritors are considered as test cases
 * In opposite case any function named "test*" either top level or situated in class named Test* or *Test is test case.
 * Providing null to "testCaseClassRequired" means "use default runner settings" and usually best value.
 * (see {@link #isTestCaseClassRequired(PsiElement)})
 * <br/>
 * If your code shouldn't depend on settings (which is true for anything but test runners),
 * consider using {@link PythonUnitTestDetectorsKt} instead.
 *
 * @author Leonid Shalupov
 * @author Ilya.Kazakevich
 */
public final class PythonUnitTestDetectorsBasedOnSettings {
  private PythonUnitTestDetectorsBasedOnSettings() {
  }

  public static boolean isTestFile(final @NotNull PyFile file,
                                   final @NotNull ThreeState testCaseClassRequired,
                                   final @Nullable TypeEvalContext context) {
    if (file.getTopLevelClasses().stream().anyMatch(o -> isTestClass(o, testCaseClassRequired, context))) {
      return true;
    }

    if (isTestCaseClassRequired(file, testCaseClassRequired)) {
      return false;
    }
    return file.getName().startsWith("test_") ||
           file.getTopLevelFunctions().stream().anyMatch(o -> isTestFunction(o, testCaseClassRequired, context));
  }

  /**
   * If element itself is test or situated inside of test
   */
  public static boolean isTestElement(final @NotNull PsiElement element, final @Nullable TypeEvalContext context) {
    final PyFunction fun = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
    if (fun != null) {
      if (isTestFunction(fun, ThreeState.UNSURE, context)) {
        return true;
      }
    }

    final PyClass clazz = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    if (clazz != null && isTestClass(clazz, ThreeState.UNSURE, context)) {
      return true;
    }

    return element instanceof PyFile && isTestFile((PyFile)element, ThreeState.UNSURE, context);
  }

  /**
   * Check if class could be test judging by it's name and methods .
   * If test class is required because of unittest, parent is also checked.
   *
   * @see PythonUnitTestDetectorsKt#isTestClass(PyClass, TypeEvalContext)
   */
  public static boolean isTestClass(final @NotNull PyClass cls,
                                    final @NotNull ThreeState testCaseClassRequired,
                                    @Nullable TypeEvalContext context) {
    if (context == null) {
      context = TypeEvalContext.codeInsightFallback(cls.getProject());
    }
    if (isTestCaseClassRequired(cls, testCaseClassRequired) && !isUnitTestCaseClass(cls, context)) {
      return false;
    }
    return PythonUnitTestDetectorsKt.isTestClass(cls, context);
  }


  /**
   * Any "test_" function is test, but it must be member of TestCase if test class required (because of unittest)
   *
   * @see PythonUnitTestDetectorsKt#isTestFunction(PyFunction)
   */
  public static boolean isTestFunction(final @NotNull PyFunction function,
                                       final @NotNull ThreeState testCaseClassRequired,
                                       final @Nullable TypeEvalContext context) {
    if (!PythonUnitTestDetectorsKt.isTestFunction(function)) {
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
   * Test class is required if "unittest" is set as test runner
   */
  public static boolean isTestCaseClassRequired(final @NotNull PsiElement anchor) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(anchor);
    if (module == null) {
      return true;
    }
    var sdk = PythonSdkUtil.findPythonSdk(module);
    if (sdk == null) {
      return true;
    }
    var factory = TestRunnerService.getInstance(module).getSelectedFactory();
    return factory.onlyClassesAreSupported(module.getProject(), sdk);
  }

  private static boolean isTestCaseClassRequired(final @NotNull PsiElement anchor, final @NotNull ThreeState userProvidedValue) {
    if (userProvidedValue != ThreeState.UNSURE) {
      return userProvidedValue.toBoolean();
    }
    return isTestCaseClassRequired(anchor);
  }
}
