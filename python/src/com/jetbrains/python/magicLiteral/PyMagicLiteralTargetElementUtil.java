package com.jetbrains.python.magicLiteral;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.Nullable;

/**
 * Supports name literal obtaining for magic literals.
 * <strong>Install it</strong> as {@link com.intellij.codeInsight.TargetElementUtilBase} service implementation
 * @author Ilya.Kazakevich
 */
class PyMagicLiteralTargetElementUtil extends TargetElementUtilBase {


  @Nullable
  @Override
  protected PsiElement getNamedElement(@Nullable final PsiElement element) {
    final PyStringLiteralExpression literalExpression = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class);
    if ((literalExpression != null) && (PyMagicLiteralTools.isMagicLiteral(literalExpression))) {
      return literalExpression;
    }
    return super.getNamedElement(element);
  }
}
