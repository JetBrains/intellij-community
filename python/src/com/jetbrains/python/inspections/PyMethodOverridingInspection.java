// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.quickfix.PyChangeSignatureQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 */
public class PyMethodOverridingInspection extends PyInspection {
  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.method.over");
  }

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
      final String name = function.getName();

      if (PyNames.INIT.equals(name) ||
          PyNames.NEW.equals(name) ||
          PyKnownDecoratorUtil.hasUnknownOrChangingSignatureDecorator(function, myTypeEvalContext) ||
          ContainerUtil.exists(PyInspectionExtension.EP_NAME.getExtensions(), e -> e.ignoreMethodParameters(function, myTypeEvalContext))) {
        return;
      }

      for (PsiElement psiElement : PySuperMethodsSearch.search(function, myTypeEvalContext)) {
        if (psiElement instanceof PyFunction) {
          final PyFunction baseMethod = (PyFunction)psiElement;
          if (!PyUtil.isSignatureCompatibleTo(function, baseMethod, myTypeEvalContext)) {
            final PyClass baseClass = baseMethod.getContainingClass();
            final String msg = PyBundle.message("INSP.signature.mismatch",
                                                cls.getName() + "." + name + "()",
                                                baseClass != null ? baseClass.getName() : "");
            registerProblem(function.getParameterList(), msg, PyChangeSignatureQuickFix.forMismatchingMethods(function, baseMethod));
          }
        }
      }
    }
  }
}
