// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows you to inject quick info into python documentation provider
 *
 * @author Ilya.Kazakevich
 */
public interface PythonDocumentationQuickInfoProvider {
  ExtensionPointName<PythonDocumentationQuickInfoProvider> EP_NAME =
    ExtensionPointName.create("Pythonid.pythonDocumentationQuickInfoProvider");

  /**
   * Return quick info for <strong>original</strong> element.
   *
   * @param originalElement original element
   * @return info (if exists) or null (if another provider should be checked)
   */
  default @Nullable @Nls String getQuickInfo(@NotNull PsiElement originalElement) {
    return null;
  }

  @ApiStatus.Experimental
  default @Nullable @Nls String getHoverAdditionalQuickInfo(@NotNull TypeEvalContext context, @Nullable PsiElement originalElement) {
    return null;
  }
}
