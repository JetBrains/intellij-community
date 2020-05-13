// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 */
public class PyMethodOverridingInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyFunction(final PyFunction function) {
      final PyClass cls = function.getContainingClass();
      if (cls == null) return;

      if (PyUtil.isInitOrNewMethod(function) ||
          PyKnownDecoratorUtil.hasUnknownOrChangingSignatureDecorator(function, myTypeEvalContext) ||
          ContainerUtil.exists(PyInspectionExtension.EP_NAME.getExtensions(), e -> e.ignoreMethodParameters(function, myTypeEvalContext))) {
        return;
      }

      for (PsiElement psiElement : PySuperMethodsSearch.search(function, myTypeEvalContext)) {
        if (psiElement instanceof PyFunction) {
          final PyFunction baseMethod = (PyFunction)psiElement;
          if (!PyUtil.isSignatureCompatibleTo(function, baseMethod, myTypeEvalContext)) {
            final PyClass baseClass = baseMethod.getContainingClass();
            final String msg = PyPsiBundle.message("INSP.signature.mismatch",
                                                cls.getName() + "." + function.getName() + "()",
                                                baseClass != null ? baseClass.getName() : "");
            registerProblem(function.getParameterList(), msg, PythonUiService.getInstance().createPyChangeSignatureQuickFixForMismatchingMethods(function, baseMethod));
          }
        }
      }
    }
  }
}
