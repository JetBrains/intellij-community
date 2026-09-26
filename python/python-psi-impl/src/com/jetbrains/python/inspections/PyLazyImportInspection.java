// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyFinallyPart;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyMatchStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyTryPart;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reports invalid placement of {@code lazy} imports (PEP 810):
 * <ul>
 *   <li>inside a function, class, {@code try}/{@code except}/{@code finally} body, or {@code match}/{@code case} body</li>
 *   <li>{@code lazy from ... import *}</li>
 *   <li>{@code lazy from __future__ import ...}</li>
 * </ul>
 */
public final class PyLazyImportInspection extends PyInspection {

  /**
   * PSI containers in which a {@code lazy} import (PEP 810) is forbidden: anything but the module level.
   */
  @SuppressWarnings("unchecked")
  public static final Class<? extends PsiElement>[] FORBIDDEN_LAZY_IMPORT_CONTAINERS = new Class[] {
    PyFunction.class, PyClass.class, PyTryPart.class, PyExceptPart.class, PyFinallyPart.class, PyMatchStatement.class
  };

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static final class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyImportStatement(@NotNull PyImportStatement node) {
      if (!node.isLazy()) return;
      checkPlacement(node);
    }

    @Override
    public void visitPyFromImportStatement(@NotNull PyFromImportStatement node) {
      if (!node.isLazy()) return;
      if (node.isStarImport()) {
        registerProblem(anchor(node), PyPsiBundle.message("INSP.lazy.import.cannot.be.star.import"),
                        ProblemHighlightType.GENERIC_ERROR);
        return;
      }
      if (node.isFromFuture()) {
        registerProblem(anchor(node), PyPsiBundle.message("INSP.lazy.import.cannot.import.from.future"),
                        ProblemHighlightType.GENERIC_ERROR);
        return;
      }
      checkPlacement(node);
    }

    private void checkPlacement(@NotNull PyStatement node) {
      if (PsiTreeUtil.getParentOfType(node, FORBIDDEN_LAZY_IMPORT_CONTAINERS) != null) {
        registerProblem(anchor(node), PyPsiBundle.message("INSP.lazy.import.must.appear.at.module.level"),
                        ProblemHighlightType.GENERIC_ERROR);
      }
    }

    private static @NotNull PsiElement anchor(@NotNull PyStatement node) {
      PsiElement first = node.getFirstChild();
      return first != null ? first : node;
    }
  }
}
