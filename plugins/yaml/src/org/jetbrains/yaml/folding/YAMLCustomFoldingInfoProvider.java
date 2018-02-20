// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.folding;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLPsiElement;

/**
 * Implement this interface to provide custom characteristics for folded YAML elements.
 * An implementation with lower order number in the extension sequence takes precedence
 */
public interface YAMLCustomFoldingInfoProvider {
  ExtensionPointName<YAMLCustomFoldingInfoProvider> EP_NAME = ExtensionPointName.create("com.intellij.yaml.customFoldingInfoProvider");

  /**
   * Returns the placeholder text for the element being folded
   *
   * @param psiElement the element
   * @return the folding text or null if the implementation has nothing to do with the element
   */
  @Nullable
  String getPlaceholderText(@NotNull YAMLPsiElement psiElement);

  /**
   * @param psiElement the element
   * @return whether the element should be collapsed by default or null if the implementation has nothing to do with the element
   */
  @Nullable
  Boolean isCollapsedByDefault(@NotNull YAMLPsiElement psiElement);
}
