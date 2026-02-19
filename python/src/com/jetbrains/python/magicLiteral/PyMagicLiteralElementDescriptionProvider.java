// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
final class PyMagicLiteralElementDescriptionProvider implements ElementDescriptionProvider {
  @Override
  public @Nullable String getElementDescription(final @NotNull PsiElement element, final @NotNull ElementDescriptionLocation location) {
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
