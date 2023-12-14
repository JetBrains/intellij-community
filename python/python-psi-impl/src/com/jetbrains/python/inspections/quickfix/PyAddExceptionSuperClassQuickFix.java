// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.lang.ASTNode;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

public class PyAddExceptionSuperClassQuickFix extends PsiUpdateModCommandQuickFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.add.exception.base");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (element instanceof PyCallExpression) {
      PyExpression callee = ((PyCallExpression)element).getCallee();
      if (callee instanceof PyReferenceExpression) {
        final PsiPolyVariantReference reference = ((PyReferenceExpression)callee).getReference();
        PsiElement psiElement = reference.resolve();
        if (psiElement instanceof PyClass) {
          final PyElementGenerator generator = PyElementGenerator.getInstance(project);
          final PyArgumentList list = ((PyClass)psiElement).getSuperClassExpressionList();
          if (list != null) {
            final PyExpression exception =
              generator.createExpressionFromText(LanguageLevel.forElement(element), "Exception");
            list.addArgument(exception);
          }
          else {
            final PyArgumentList expressionList = generator.createFromText(
              LanguageLevel.forElement(element), PyClass.class, "class A(Exception): pass").getSuperClassExpressionList();
            assert expressionList != null;
            final ASTNode nameNode = ((PyClass)psiElement).getNameNode();
            assert nameNode != null;
            final PsiElement oldArgList = nameNode.getPsi().getNextSibling();
            oldArgList.replace(expressionList);
          }
        }
      }
    }
  }

}
