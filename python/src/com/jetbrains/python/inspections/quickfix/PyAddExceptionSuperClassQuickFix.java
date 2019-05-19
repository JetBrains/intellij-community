// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

public class PyAddExceptionSuperClassQuickFix implements LocalQuickFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("QFIX.NAME.add.exception.base");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
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
