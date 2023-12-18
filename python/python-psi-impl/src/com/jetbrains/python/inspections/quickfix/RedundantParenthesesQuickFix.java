// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.lang.ASTNode;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to remove redundant parentheses from if/while/except statement
 */
public class RedundantParenthesesQuickFix extends PsiUpdateModCommandQuickFix {
  private static final Logger LOG = Logger.getInstance(RedundantParenthesesQuickFix.class);

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.redundant.parentheses");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (element instanceof PyParenthesizedExpression) {
      PsiElement binaryExpression = ((PyParenthesizedExpression)element).getContainedExpression();
      PyBinaryExpression parent = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class);
      if (binaryExpression instanceof PyBinaryExpression && parent != null) {
        if (!replaceBinaryExpression((PyBinaryExpression)binaryExpression)) {
          element.replace(binaryExpression);
        }
      }
      else {
        final PyExpression content = PyPsiUtils.flattenParens((PyParenthesizedExpression)element);
        if (content != null) {
          element.replace(content);
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
