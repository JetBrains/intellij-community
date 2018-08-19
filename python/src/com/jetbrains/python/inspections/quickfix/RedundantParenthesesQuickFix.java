// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
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
  private static final Logger LOG = Logger.getInstance(RedundantParenthesesQuickFix.class);

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("QFIX.redundant.parentheses");
  }

  @Override
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
      LOG.assertTrue(element.getParent() instanceof PyClass, "Parent type: " + element.getParent().getClass());
      LOG.assertTrue(((PyArgumentList)element).getArguments().length == 0, "Argument list: " + element.getText());
      final ASTNode nameNode = PyElementGenerator.getInstance(project).createFromText(
        LanguageLevel.forElement(element), PyClass.class, "class A: pass").getNameNode();
      if (nameNode != null) {
        final PsiElement emptyArgList = nameNode.getPsi().getNextSibling();
        element.replace(emptyArgList);
      }
      else {
        element.delete();
      }
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
