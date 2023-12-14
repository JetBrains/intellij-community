// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class PyAddPropertyForFieldQuickFix extends PsiUpdateModCommandQuickFix {
  private final @IntentionFamilyName String myName;

  public PyAddPropertyForFieldQuickFix(@NotNull @IntentionFamilyName String name) {
    myName = name;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return myName;
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (element instanceof PyReferenceExpression) {
      final PsiReference reference = element.getReference();
      final PsiElement resolved = updater.getWritable(reference.resolve());
      if (resolved instanceof PyTargetExpression target) {
        final PyClass containingClass = target.getContainingClass();
        if (containingClass != null) {
          final String name = target.getName();
          if (name == null) return;
          String propertyName = StringUtil.trimStart(name, "_");
          final Map<String,Property> properties = containingClass.getProperties();
          final PyElementGenerator generator = PyElementGenerator.getInstance(project);
          if (!properties.containsKey(propertyName)) {
            final PyFunction property = generator.createProperty(LanguageLevel.forElement(containingClass), propertyName, name, AccessDirection.READ);
            PyPsiRefactoringUtil.addElementToStatementList(property, containingClass.getStatementList(), false);
          }
          final PyExpression qualifier = ((PyReferenceExpression)element).getQualifier();
          if (qualifier != null) {
            String newElementText = qualifier.getText() + "." + propertyName;
            final PyExpression newElement = generator.createExpressionFromText(LanguageLevel.forElement(containingClass), newElementText);
            element.replace(newElement);
          }
        }
      }
    }
  }
}
