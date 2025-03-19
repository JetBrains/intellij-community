// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * Quickfix to introduce variable if statement seems to have no effect
 */
public class StatementEffectIntroduceVariableQuickFix extends PsiUpdateModCommandQuickFix {
  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.introduce.variable");
  }

  @Override
  public void applyFix(final @NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    final PyAssignmentStatement assignment = elementGenerator.createFromText(LanguageLevel.forElement(element), PyAssignmentStatement.class,
                                                                             "var = " + element.getText());

    element = element.replace(assignment);
    if (element == null) return;
    element = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(element);
    if (element == null) return;
    final PyExpression leftHandSideExpression = ((PyAssignmentStatement)element).getLeftHandSideExpression();
    assert leftHandSideExpression != null;
    updater.templateBuilder().field(leftHandSideExpression, "var");
    }
}
