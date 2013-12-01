package org.jetbrains.postfixCompletion.templates;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

abstract public class BooleanPostfixTemplate2 extends PostfixTemplate {
  public PsiExpression getTopmostExpression(PsiElement context) {
    return PsiTreeUtil.getTopmostParentOfType(context, PsiExpression.class);
  }

  @Override
  public boolean isMyContext(@NotNull PsiElement context) {
    // todo: check boolean type
    PsiExpression topmostExpression = getTopmostExpression(context);
    return topmostExpression != null && topmostExpression.getParent() instanceof PsiStatement;
  }
}
