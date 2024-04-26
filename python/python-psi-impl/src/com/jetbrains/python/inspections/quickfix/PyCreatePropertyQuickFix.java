// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import org.jetbrains.annotations.NotNull;

public class PyCreatePropertyQuickFix extends PsiUpdateModCommandQuickFix {
  private final AccessDirection myAccessDirection;

  public PyCreatePropertyQuickFix(AccessDirection dir) {
    myAccessDirection = dir;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.create.property");
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final PsiElement element, @NotNull ModPsiUpdater updater) {
    if (element instanceof PyQualifiedExpression) {
      final PyExpression qualifier = ((PyQualifiedExpression)element).getQualifier();
      if (qualifier != null) {
        final PyType type = TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile()).getType(qualifier);
        if (type instanceof PyClassType) {
          final PyClass cls = ((PyClassType)type).getPyClass();
          final String propertyName = ((PyQualifiedExpression)element).getName();
          if (propertyName == null) return;
          final String fieldName = "_" + propertyName;
          final PyElementGenerator generator = PyElementGenerator.getInstance(project);
          final PyFunction property = generator.createProperty(LanguageLevel.forElement(cls), propertyName, fieldName, myAccessDirection);
          final PyStatementList statementsList = updater.getWritable(cls.getStatementList());
          PyPsiRefactoringUtil.addElementToStatementList(property, statementsList, myAccessDirection == AccessDirection.READ);
        }
      }
    }
  }
}
