package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiExpression;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.debugger.codeinsight.JavaWithRuntimeCastSurrounder;
import com.intellij.featureStatistics.FeatureUsageTracker;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class JavaExpressionSurroundDescriptor implements SurroundDescriptor {
  private static final Surrounder[] SURROUNDERS = {
    new JavaWithParenthesesSurrounder(),
      new JavaWithCastSurrounder(),
      new JavaWithRuntimeCastSurrounder(),
      new JavaWithNotSurrounder(),
      new JavaWithNotInstanceofSurrounder(),
      new JavaWithIfExpressionSurrounder(),
      new JavaWithIfElseExpressionSurrounder()
  };

  @NotNull public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    final PsiExpression expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
    if (expr == null) return PsiElement.EMPTY_ARRAY;
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.surroundwith.expression");
    return new PsiElement[] {expr};
  }

  @NotNull public Surrounder[] getSurrounders() {
    return SURROUNDERS;
  }
}
