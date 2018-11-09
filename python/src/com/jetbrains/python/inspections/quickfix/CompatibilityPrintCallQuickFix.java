// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to replace statement that has no effect with function call
 */
public class CompatibilityPrintCallQuickFix implements LocalQuickFix {
  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("QFIX.statement.effect");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement expression = descriptor.getPsiElement();
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    replacePrint(expression, elementGenerator);
  }

  private static void replacePrint(PsiElement expression, PyElementGenerator elementGenerator) {
    final StringBuilder stringBuilder = new StringBuilder("print(");
    final PyFile file = (PyFile)expression.getContainingFile();
    final PyExpression[] target = PsiTreeUtil.getChildrenOfType(expression, PyExpression.class);
    if (target != null) {
      stringBuilder.append(StringUtil.join(target, o -> o.getText(), ", "));
    }
    stringBuilder.append(")");
    expression.replace(elementGenerator.createFromText(LanguageLevel.forElement(expression), PyElement.class,
                                                       stringBuilder.toString()));

    final PyFromImportStatement statement = elementGenerator.createFromText(LanguageLevel.forElement(expression), PyFromImportStatement.class,
                                                                      "from __future__ import print_function");
    file.addBefore(statement, file.getStatements().get(0));
  }
}
