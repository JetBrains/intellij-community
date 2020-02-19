// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResolveUtil {
  /**
   * Go through the whole elements from the bottom to the top (all siblings should be visited).
   * In checking function declaration we should not visit its entire body.
   *
   * @param element             - psi element which siblings we should check
   * @param lastParentTextRange - text range of the previous element
   * @param processor           - element processor
   * @param state               - context of the processor work
   * @return false to stop processing.
   */
  public static boolean processFunctionDeclarations(@Nullable PsiElement element, @NotNull TextRange lastParentTextRange,
                                                    @NotNull PsiScopeProcessor processor, @NotNull ResolveState state) {
    if (element == null) return true;
    for (PsiElement e = element; e != null; e = e.getPrevSibling()) {
      if (violateTextRangeRestrictions(e.getTextRange(), lastParentTextRange)) continue;
      if (!processor.execute(e, state) || (!violateRestrictions(e) && !processFunctionDeclarations(e.getLastChild(), lastParentTextRange,
                                                                                                   processor, state))) {
        return false;
      }
    }
    return true;
  }

  public static boolean processChildren(@NotNull PsiElement element,
                                 @NotNull PsiScopeProcessor processor,
                                 @NotNull ResolveState substitutor,
                                 @Nullable PsiElement lastParent,
                                 @NotNull PsiElement place) {
    PsiElement run = lastParent == null ? element.getLastChild() : lastParent.getPrevSibling();

    while (run != null) {
      if (run instanceof ShCompositeElement && !run.processDeclarations(processor, substitutor, element, place)) {
        return false;
      }
      run = run.getPrevSibling();
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
