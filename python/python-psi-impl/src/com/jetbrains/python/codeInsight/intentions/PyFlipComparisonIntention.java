// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class PyFlipComparisonIntention extends PsiUpdateModCommandAction<PsiElement> {
  PyFlipComparisonIntention() {
    super(PsiElement.class);
  }

  private static class Holder {
    private static final Map<PyElementType, String> FLIPPED_OPERATORS = Map.of(
      PyTokenTypes.EQEQ, "==",
      PyTokenTypes.NE, "!=",
      PyTokenTypes.NE_OLD, "<>",
      PyTokenTypes.GE, "<=",
      PyTokenTypes.LE, ">=",
      PyTokenTypes.GT, "<",
      PyTokenTypes.LT, ">");
  }


  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (!(context.file() instanceof PyFile)) {
      return null;
    }

    PyBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class, false);
    while (binaryExpression != null) {
      PyElementType operator = binaryExpression.getOperator();
      if (operator != null && Holder.FLIPPED_OPERATORS.containsKey(operator)) {
        String operatorText = binaryExpression.getPsiOperator().getText();
        String flippedOperatorText = Holder.FLIPPED_OPERATORS.get(operator);
        if (flippedOperatorText.equals(operatorText)) {
          return Presentation.of(PyPsiBundle.message("INTN.flip.comparison", operatorText));
        }
        else {
         return Presentation.of(PyPsiBundle.message("INTN.flip.comparison.to.operator", operatorText, flippedOperatorText));
        }
      }
      binaryExpression = PsiTreeUtil.getParentOfType(binaryExpression, PyBinaryExpression.class);
    }
    return null;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.flip.comparison");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PyBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class, false);
    while (binaryExpression != null) {
      PyElementType operator = binaryExpression.getOperator();
      if (operator != null && Holder.FLIPPED_OPERATORS.containsKey(operator)) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(context.project());
        binaryExpression.replace(elementGenerator.createBinaryExpression(Holder.FLIPPED_OPERATORS.get(operator),
                                                                         binaryExpression.getRightExpression(),
                                                                         binaryExpression.getLeftExpression()));
        return;
      }
      binaryExpression = PsiTreeUtil.getParentOfType(binaryExpression, PyBinaryExpression.class);
    }
  }
}
