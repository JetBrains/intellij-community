// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.quickfix.PyAddExceptionSuperClassQuickFix;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyRaiseStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class PyExceptionInheritInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyRaiseStatement(@NotNull PyRaiseStatement node) {
      PyExpression[] expressions = node.getExpressions();
      if (expressions.length == 0) {
        return;
      }
      PyExpression expression = expressions[0];
      if (expression instanceof PyCallExpression) {
        PyExpression callee = ((PyCallExpression)expression).getCallee();
        if (callee instanceof PyReferenceExpression) {
          final PsiPolyVariantReference reference = ((PyReferenceExpression)callee).getReference(getResolveContext());
          PsiElement psiElement = reference.resolve();
          if (psiElement instanceof PyClass aClass) {
            List<PyClassLikeType> selfAndAncestorTypes = ContainerUtil.prepend(aClass.getAncestorTypes(myTypeEvalContext),
                                                                               aClass.getType(myTypeEvalContext));
            for (PyClassLikeType type : selfAndAncestorTypes) {
              if (type == null) {
                return;
              }
              final String name = type.getName();
              if (name == null || "BaseException".equals(name)) {
                return;
              }
            }
            registerProblem(expression,
                            PyPsiBundle.message("INSP.exception.inheritance.exception.does.not.inherit.from.base.exception.class"),
                            new PyAddExceptionSuperClassQuickFix());
          }
        }
      }
    }
  }
}
