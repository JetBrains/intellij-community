// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.google.common.collect.Lists;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class PyMoveExceptQuickFix extends PsiUpdateModCommandQuickFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.move.except.up");
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final PsiElement element, @NotNull ModPsiUpdater updater) {
    final PyExceptPart part = PsiTreeUtil.getParentOfType(element, PyExceptPart.class);
    if (part == null) return;
    final PyExpression exceptClassExpression = part.getExceptClass();
    if (exceptClassExpression == null) return;

    final var resolveContext = PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(project));
    final PsiElement exceptClass = ((PyReferenceExpression)exceptClassExpression).followAssignmentsChain(resolveContext).getElement();
    if (exceptClass instanceof PyClass) {
      final PyTryExceptStatement statement = PsiTreeUtil.getParentOfType(part, PyTryExceptStatement.class);
      if (statement == null) return;

      PyExceptPart prevExceptPart = PsiTreeUtil.getPrevSiblingOfType(part, PyExceptPart.class);
      final ArrayList<PyClass> superClasses = Lists.newArrayList(((PyClass)exceptClass).getSuperClasses(null));
      while (prevExceptPart != null) {
        final PyExpression classExpression = prevExceptPart.getExceptClass();
        if (classExpression == null) return;
        final PsiElement aClass = ((PyReferenceExpression)classExpression).followAssignmentsChain(resolveContext).getElement();
        if (aClass instanceof PyClass) {
          if (superClasses.contains(aClass)) {
            statement.addBefore(part, prevExceptPart);
            part.delete();
            return;
          }
        }
        prevExceptPart = PsiTreeUtil.getPrevSiblingOfType(prevExceptPart, PyExceptPart.class);
      }
    }
  }
}
