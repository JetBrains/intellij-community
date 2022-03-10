// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.psi.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PyMakeMethodStaticQuickFix implements LocalQuickFix {
  public PyMakeMethodStaticQuickFix() {
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.make.static");
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (problemFunction == null) return;

    List<PyReferenceExpression> usages = StreamEx.of(PyPsiIndexUtil.findUsages(problemFunction, false))
      .map(UsageInfo::getElement)
      .select(PyReferenceExpression.class)
      .toList();

    ApplicationManagerEx.getApplicationEx().runWriteActionWithCancellableProgressInDispatchThread(
      PyPsiBundle.message("refactoring.progress.title.updating.existing.usages"), problemFunction.getProject(), null, (indicator -> {
        updateDefinition(problemFunction);
        for (int i = 0; i < usages.size(); i++) {
          indicator.checkCanceled();
          indicator.setFraction((i + 1.0) / usages.size());
          updateUsage(usages.get(i));
        }
      })
    );
  }

  private static void updateDefinition(@NotNull PyFunction function) {
    final PyParameter[] parameters = function.getParameterList().getParameters();
    if (parameters.length > 0) {
      parameters[0].delete();
    }
    PyUtil.addDecorator(function, "@" + PyNames.STATICMETHOD);
  }

  private static void updateUsage(@NotNull final PyReferenceExpression element) {
    final PyExpression qualifier = element.getQualifier();
    if (qualifier == null) return;
    final PsiReference reference = qualifier.getReference();
    if (reference == null) return;
    final PsiElement resolved = reference.resolve();
    if (resolved instanceof PyClass) {     //call with first instance argument A.m(A())
      updateArgumentList(element);
    }
  }

  private static void updateArgumentList(@NotNull final PyReferenceExpression element) {
    final PyCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
    if (callExpression == null) return;
    final PyArgumentList argumentList = callExpression.getArgumentList();
    if (argumentList == null) return;
    final PyExpression[] arguments = argumentList.getArguments();
    if (arguments.length > 0) {
      arguments[0].delete();
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return currentFile;
  }
}
