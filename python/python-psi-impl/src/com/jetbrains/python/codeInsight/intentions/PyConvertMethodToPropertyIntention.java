// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PyConvertMethodToPropertyIntention extends PyBaseIntentionAction {
  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.convert.method.to.property");
  }

  @Override
  public @NotNull String getText() {
    return PyPsiBundle.message("INTN.convert.method.to.property");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (!(psiFile instanceof PyFile)) {
      return false;
    }
    final PsiElement element = PyUtil.findNonWhitespaceAtOffset(psiFile, editor.getCaretModel().getOffset());
    final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (function == null) return false;
    final PyClass containingClass = function.getContainingClass();
    if (containingClass == null) return false;
    if (function.getParameterList().getParameters().length > 1) return false;

    final PyDecoratorList decoratorList = function.getDecoratorList();
    if (decoratorList != null) return false;

    final boolean[] available = {false};
    function.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReturnStatement(@NotNull PyReturnStatement node) {
        if (node.getExpression() != null)
          available[0] = true;
      }

      @Override
      public void visitPyYieldExpression(@NotNull PyYieldExpression node) {
        available[0] = true;
      }
    });

    return available[0];
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (problemFunction == null) return;
    final PyClass containingClass = problemFunction.getContainingClass();
    if (containingClass == null) return;
    final List<UsageInfo> usages = PyPsiIndexUtil.findUsages(problemFunction, false);

    if (!prepareForWrite(file, usages)) return;

    WriteAction.run(() -> {
      PyUtil.addDecorator(problemFunction, "@" + PyNames.PROPERTY);
      deleteUsages(usages);
    });
  }

  private static boolean prepareForWrite(PsiFile file, List<UsageInfo> usages) {
    List<PsiElement> toWrite = ContainerUtil.prepend(ContainerUtil.mapNotNull(usages, UsageInfo::getElement), file);
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(toWrite)) return false;
    return true;
  }

  private static void deleteUsages(List<UsageInfo> usages) {
    for (UsageInfo usage : usages) {
      final PsiElement usageElement = usage.getElement();
      if (usageElement instanceof PyReferenceExpression) {
        final PsiElement parent = usageElement.getParent();
        if (parent instanceof PyCallExpression callExpression) {
          convertCallExpToRefExpr(callExpression);
        }
      }
    }
  }

  private static void convertCallExpToRefExpr(PyCallExpression callExpression) {
    PyExpression callee = callExpression.getCallee();
    if (callee == null) {
      throw new PsiInvalidElementAccessException(callExpression);
    }

    String newReferenceExpressionText = callee.getText();

    PyExpression newExpression = PyElementGenerator.getInstance(callExpression.getProject())
      .createExpressionFromText(LanguageLevel.forElement(callExpression), newReferenceExpressionText);

    if (newExpression instanceof PyReferenceExpression referenceExpression) {
      callExpression.replace(referenceExpression);
    }
    else {
      throw new IllegalStateException(
        "Failed to create reference expression from call expression with text: \"" + callExpression.getText() + "\"");
    }
  }
}
