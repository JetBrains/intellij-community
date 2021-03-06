/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * TODO: Move methods to {@link PyCustomMember}. Only dependency hell prevents me from doing it
 */
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
  @NotNull
  public static LookupElementBuilder toLookUpElement(@NotNull final PyCustomMember member, @Nullable final String typeText) {

    LookupElementBuilder lookupElementBuilder = LookupElementBuilder.create(member.getName())
      .withIcon(member.getIcon())
      .withTypeText(typeText);
    if (member.isFunction()) {
      lookupElementBuilder = lookupElementBuilder.withInsertHandler(ParenthesesInsertHandler.NO_PARAMETERS).withLookupString("()");
    }
    return lookupElementBuilder;
  }
}
