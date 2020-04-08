// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

public class TransformClassicClassQuickFix implements LocalQuickFix {
  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.classic.class.transform");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement psiElement = descriptor.getPsiElement();
    psiElement = PsiTreeUtil.getParentOfType(psiElement, PyClass.class);
    if (psiElement != null) {
      PyClass pyClass = (PyClass) psiElement;
      PyExpression[] superClassExpressions = pyClass.getSuperClassExpressions();
      PyElementGenerator generator = PyElementGenerator.getInstance(project);
      if (superClassExpressions.length == 0) {
        pyClass.replace(generator.createFromText(LanguageLevel.getDefault(), PyClass.class,
                                                 "class " + pyClass.getName() + "(" +
                                                 PyNames.OBJECT + "):\n    " + pyClass.getStatementList().getText()));
      } else {
        StringBuilder stringBuilder = new StringBuilder("class ");
        stringBuilder.append(pyClass.getName()).append("(");
        for (PyExpression expression: superClassExpressions) {
          stringBuilder.append(expression.getText()).append(", ");
        }
        stringBuilder.append(PyNames.OBJECT).append(":\n    ");
        stringBuilder.append(pyClass.getStatementList().getText());
        pyClass.replace(generator.createFromText(LanguageLevel.getDefault(), PyClass.class, stringBuilder.toString()));
      }
    }
  }
}
