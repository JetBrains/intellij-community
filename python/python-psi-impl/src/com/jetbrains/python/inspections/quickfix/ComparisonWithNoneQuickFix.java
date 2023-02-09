// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

public class ComparisonWithNoneQuickFix implements LocalQuickFix {
  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.replace.equality");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement problemElement = descriptor.getPsiElement();
    if (problemElement instanceof PyBinaryExpression binaryExpression) {
      PyElementType operator = binaryExpression.getOperator();
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      String temp;
      temp = (operator == PyTokenTypes.EQEQ) ? "is" : "is not";
      PyExpression expression = elementGenerator.createBinaryExpression(temp,
                                                                        binaryExpression.getLeftExpression(),
                                                                        binaryExpression.getRightExpression());
      binaryExpression.replace(expression);
    }
  }
}
