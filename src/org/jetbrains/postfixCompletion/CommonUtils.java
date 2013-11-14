package org.jetbrains.postfixCompletion;

import com.intellij.psi.*;
import org.jetbrains.annotations.*;

public abstract class CommonUtils {
  private CommonUtils() { }

  public static boolean isNiceExpression(@NotNull PsiExpression expression) {
    if (expression instanceof PsiAssignmentExpression) return false;
    if (expression instanceof PsiPrefixExpression) return false;
    if (expression instanceof PsiPostfixExpression) return false;

    if (expression instanceof PsiMethodCallExpression) {
      PsiType expressionType = expression.getType();
      if (expressionType != null && expressionType.equals(PsiType.VOID)) return false;
    }

    return true;
  }
}
