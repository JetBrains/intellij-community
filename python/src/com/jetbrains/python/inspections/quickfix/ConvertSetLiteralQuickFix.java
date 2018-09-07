// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

public class ConvertSetLiteralQuickFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("INTN.convert.set.literal.to");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.convert.set.literal");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement setLiteral = descriptor.getPsiElement();
    if (setLiteral instanceof PySetLiteralExpression) {
      PyExpression[] expressions = ((PySetLiteralExpression)setLiteral).getElements();
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      assert expressions.length != 0;
      StringBuilder stringBuilder = new StringBuilder(expressions[0].getText());
      for (int i = 1; i < expressions.length; ++i) {
        stringBuilder.append(", ");
        stringBuilder.append(expressions[i].getText());
      }
      PyStatement newElement = elementGenerator.createFromText(LanguageLevel.getDefault(), PyExpressionStatement.class, "set([" + stringBuilder.toString() + "])");
      final PsiElement parent = setLiteral.getParent();
      if (parent instanceof PyExpressionStatement) {
        parent.replace(newElement);
      }
    }
  }
}
