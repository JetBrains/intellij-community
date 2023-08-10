// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This extension point enables in-text (e.g. without angle brackets) HTML completion for certain PSI files.
 * It is a temporary solution to the problem of HTML completion in multi-view files.
 */
@ApiStatus.Internal
public interface HtmlInTextCompletionEnabler {
  ExtensionPointName<HtmlInTextCompletionEnabler> EP_NAME =
    ExtensionPointName.create("com.intellij.completion.htmlInTextCompletionEnabler");

  boolean isEnabledInFile(final @NotNull PsiFile file);
}
