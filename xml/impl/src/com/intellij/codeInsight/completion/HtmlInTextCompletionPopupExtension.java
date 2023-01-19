// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This extension point allows changing the behavior of the popup for in-text (e.g. without angle brackets) HTML completion
 * for certain PSI elements.
 */
@ApiStatus.Internal
public interface HtmlInTextCompletionPopupExtension {
  ExtensionPointName<HtmlInTextCompletionPopupExtension> EP_NAME =
    ExtensionPointName.create("com.intellij.completion.htmlInTextCompletionPopupExtension");

  boolean isDeselectingFirstItemDisabled(final @NotNull PsiElement element);
}
