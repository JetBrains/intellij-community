// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * TODO: Move methods to {@link PyCustomMember}. Only dependency hell prevents me from doing it
 */
@ApiStatus.Internal
public final class PyCustomMemberUtils {
  private PyCustomMemberUtils() {
  }

  /**
   * Creates {@link com.intellij.codeInsight.lookup.LookupElement} to be used in cases like {@link com.jetbrains.python.psi.types.PyType#getCompletionVariants(String, com.intellij.psi.PsiElement, com.intellij.util.ProcessingContext)}
   * This method should be in {@link PyCustomMember} but it does not. We need to move it.
   *
   * @param member custom member
   * @param typeText type text (if any)
   * @return lookup element
   */
  public static @NotNull LookupElementBuilder toLookUpElement(final @NotNull PyCustomMember member, final @Nullable String typeText) {

    LookupElementBuilder lookupElementBuilder = LookupElementBuilder.create(member.getName())
      .withIcon(member.getIcon())
      .withTypeText(typeText);
    if (member.isFunction()) {
      lookupElementBuilder = lookupElementBuilder.withInsertHandler(ParenthesesInsertHandler.NO_PARAMETERS).withLookupString("()");
    }
    return lookupElementBuilder;
  }
}
