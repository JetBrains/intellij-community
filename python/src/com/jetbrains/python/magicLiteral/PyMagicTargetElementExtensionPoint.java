package com.jetbrains.python.magicLiteral;

import com.intellij.codeInsight.TargetElementExtensionPoint;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Makes magic literals work as target elements (to support renaming)
 * <strong>Install it</strong> as {@link com.intellij.codeInsight.TargetElementExtensionPoint}
 *
 * @author Ilya.Kazakevich
 */
class PyMagicTargetElementExtensionPoint implements TargetElementExtensionPoint {
  @Nullable
  @Override
  public PsiElement getNearestTargetElement(@NotNull final PsiElement element) {
    final PyStringLiteralExpression literalExpression = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class);
    if ((literalExpression != null) && (PyMagicLiteralTools.isMagicLiteral(literalExpression))) {
      return literalExpression;
    }
    return null;
  }
}
