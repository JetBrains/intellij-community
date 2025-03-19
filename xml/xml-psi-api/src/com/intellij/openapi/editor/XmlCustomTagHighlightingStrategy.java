// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a strategy for highlighting custom XML tags such as components in web frameworks.
 */
@ApiStatus.Experimental
public interface XmlCustomTagHighlightingStrategy {
  ExtensionPointName<XmlCustomTagHighlightingStrategy> EP_NAME =
    new ExtensionPointName<>("com.intellij.xml.xmlCustomTagHighlightingStrategy");

  /**
   * Checks whether this highlighting strategy is available for the specified tag in the specified file.
   *
   * @param file the PsiFile to check for availability
   * @param xmlTag the XML tag to be tested
   * @return true if the strategy is available, false otherwise
   */
  boolean isAvailable(@NotNull PsiFile file, @NotNull XmlTag xmlTag);

  /**
   * Highlights a custom XML tag in the specified PsiFile.
   *
   * @param file    the PsiFile containing the custom XML tag
   * @param xmlTag  the custom XML tag to be highlighted
   * @return the TextAttributesKey used to highlight the custom XML tag,
   *                or null if the custom tag cannot be highlighted
   */
  @Nullable TextAttributesKey highlightCustomTag(@NotNull PsiFile file, @NotNull XmlTag xmlTag);
}
