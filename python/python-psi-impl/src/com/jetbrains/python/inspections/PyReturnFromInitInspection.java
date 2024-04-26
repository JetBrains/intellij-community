// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.quickfix.PyRemoveStatementQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Checks that no value is returned from __init__().
 */
public final class PyReturnFromInitInspection extends PyInspection {

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
    public void visitPyFunction(@NotNull PyFunction function) {
      if (PyUtil.isInitMethod(function)) {
        Collection<PsiElement> offenders = new ArrayList<>();
        findReturnValueInside(function, offenders);
        for (PsiElement offender : offenders) {
          registerProblem(offender, PyPsiBundle.message("INSP.cant.return.value.from.init"), new PyRemoveStatementQuickFix());
        }
      }
    }

    private static void findReturnValueInside(@NotNull PsiElement node, Collection<PsiElement> offenders) {
      for (PsiElement child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child instanceof PyFunction || child instanceof PyClass) continue; // ignore possible inner functions and classes
        if (child instanceof PyReturnStatement) {
          if (((PyReturnStatement)child).getExpression() != null) offenders.add(child);
        }
        findReturnValueInside(child, offenders);
      }
    }
  }
}
