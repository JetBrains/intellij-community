/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to remove redundant parentheses from if/while/except statement
 */
public class RedundantParenthesesQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.redundant.parentheses");
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element instanceof PyParenthesizedExpression) {
      PsiElement binaryExpression = ((PyParenthesizedExpression)element).getContainedExpression();
      PyBinaryExpression parent = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class);
      if (binaryExpression instanceof PyBinaryExpression && parent != null) {
        if (!replaceBinaryExpression((PyBinaryExpression)binaryExpression)) {
          element.replace(binaryExpression);
        }
      }
      else {
        while (element instanceof PyParenthesizedExpression) {
          PyExpression expression = ((PyParenthesizedExpression)element).getContainedExpression();
          if (expression != null) {
            element = element.replace(expression);
          }
        }
      }
    }
    else if (element instanceof PyArgumentList) {
      assert element.getParent() instanceof PyClass && ((PyArgumentList)element).getArguments().length == 0;
      element.delete();
    }
  }

  private static boolean replaceBinaryExpression(PyBinaryExpression element) {
    PyExpression left = element.getLeftExpression();
    PyExpression right = element.getRightExpression();
    if (left instanceof PyParenthesizedExpression &&
        right instanceof PyParenthesizedExpression) {
      PyExpression leftContained = ((PyParenthesizedExpression)left).getContainedExpression();
      PyExpression rightContained = ((PyParenthesizedExpression)right).getContainedExpression();
      if (leftContained != null && rightContained != null &&
          !(leftContained instanceof PyTupleExpression) && !(rightContained instanceof PyTupleExpression)) {
        left.replace(leftContained);
        right.replace(rightContained);
        return true;
      }
    }
    return false;
  }
}
