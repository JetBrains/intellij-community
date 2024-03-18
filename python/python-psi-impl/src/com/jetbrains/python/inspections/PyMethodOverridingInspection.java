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
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyMethodOverridingInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyFunction(final @NotNull PyFunction function) {
      final PyClass cls = function.getContainingClass();
      if (cls == null) return;

      if (PyUtil.isInitOrNewMethod(function) ||
          PyKnownDecoratorUtil.hasUnknownOrChangingSignatureDecorator(function, myTypeEvalContext) ||
          ContainerUtil.exists(PyInspectionExtension.EP_NAME.getExtensions(), e -> e.ignoreMethodParameters(function, myTypeEvalContext))) {
        return;
      }

      for (PsiElement psiElement : PySuperMethodsSearch.search(function, myTypeEvalContext)) {
        if (psiElement instanceof PyFunction baseMethod) {
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
