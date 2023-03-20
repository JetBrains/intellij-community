// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ResolveUtil {
  public static boolean processChildren(@NotNull PsiElement element,
                                 @NotNull PsiScopeProcessor processor,
                                 @NotNull ResolveState substitutor,
                                 @Nullable PsiElement lastParent,
                                 @NotNull PsiElement place) {
    PsiElement[] children = element.getChildren();
    if (children.length == 0) return true;
    TextRange placeTextRange = place.getTextRange();
    for (int i = children.length - 1; i >= 0; i--) {
      PsiElement child = children[i];
      if (violateRestrictions(element)) continue;
      if (violateTextRangeRestrictions(child.getTextRange(), placeTextRange)) continue;
      if (!child.processDeclarations(processor, substitutor, element, place)) return false;
    }
    return true;
  }

  private static boolean violateTextRangeRestrictions(@NotNull TextRange elementTextRange, @NotNull TextRange lastParentTextRange) {
    // If the element not in the range of parent or declared lower than called
    return !elementTextRange.contains(lastParentTextRange) && elementTextRange.getEndOffset() > lastParentTextRange.getStartOffset();
  }

  private static boolean violateRestrictions(@NotNull PsiElement element) {
    return element instanceof ShFunctionDefinition;
  }
}
