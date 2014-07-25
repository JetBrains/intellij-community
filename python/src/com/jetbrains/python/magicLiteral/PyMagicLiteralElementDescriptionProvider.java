package com.jetbrains.python.magicLiteral;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewLongNameLocation;
import com.intellij.usageView.UsageViewShortNameLocation;
import com.intellij.usageView.UsageViewTypeLocation;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides description for magic literals.
 * <strong>Install it</strong> as {@link ElementDescriptionProvider#EP_NAME}
 *
 * @author Ilya.Kazakevich
 */
class PyMagicLiteralElementDescriptionProvider implements ElementDescriptionProvider {
  @Nullable
  @Override
  public String getElementDescription(@NotNull final PsiElement element, @NotNull final ElementDescriptionLocation location) {
    if (element instanceof PyStringLiteralExpression) {
      final PyMagicLiteralExtensionPoint point = PyMagicLiteralTools.getPoint((PyStringLiteralExpression)element);
      if (point != null) {
        if (location instanceof UsageViewTypeLocation) {
          return point.getLiteralType();
        }
        if ((location instanceof UsageViewShortNameLocation) || (location instanceof UsageViewLongNameLocation)) {
          return ((StringLiteralExpression)element).getStringValue();
        }
      }
    }
    return null;
  }
}
