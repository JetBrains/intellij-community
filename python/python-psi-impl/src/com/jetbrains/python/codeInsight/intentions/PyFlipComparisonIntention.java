// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class PyFlipComparisonIntention extends PsiUpdateModCommandAction<PyBinaryExpression> {
  PyFlipComparisonIntention() {
    super(PyBinaryExpression.class);
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
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PyBinaryExpression element) {
    while (element != null) {
      PyElementType operator = element.getOperator();
      if (operator != null && Holder.FLIPPED_OPERATORS.containsKey(operator)) {
        String operatorText = element.getPsiOperator().getText();
        String flippedOperatorText = Holder.FLIPPED_OPERATORS.get(operator);
        if (flippedOperatorText.equals(operatorText)) {
          return Presentation.of(PyPsiBundle.message("INTN.flip.comparison", operatorText));
        }
        else {
         return Presentation.of(PyPsiBundle.message("INTN.flip.comparison.to.operator", operatorText, flippedOperatorText));
        }
      }
      element = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class);
    }
    return null;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.flip.comparison");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PyBinaryExpression element, @NotNull ModPsiUpdater updater) {
    while (element != null) {
      PyElementType operator = element.getOperator();
      if (operator != null && Holder.FLIPPED_OPERATORS.containsKey(operator)) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(context.project());
        element.replace(elementGenerator.createBinaryExpression(Holder.FLIPPED_OPERATORS.get(operator),
                                                                         element.getRightExpression(),
                                                                         element.getLeftExpression()));
        return;
      }
      element = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class);
    }
  }
}
