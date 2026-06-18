// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi

import com.intellij.lang.ASTNode
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point that allows plugins to provide custom [PsiElement] implementations for Markdown AST nodes.
 *
 * Providers are used only for element types that the Markdown plugin does not handle itself.
 * Built-in element types keep their default PSI.
 */
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
interface MarkdownPsiElementProvider {
  /**
   * @return a custom [PsiElement] for [node], or `null` to let other providers or the default factory handle it.
   */
  fun createElement(node: ASTNode): PsiElement?

  companion object {
    @ApiStatus.Internal
    val extensionPoint: ExtensionPointName<MarkdownPsiElementProvider> =
      ExtensionPointName("org.intellij.markdown.markdownElementFactory")

    internal fun createElement(node: ASTNode): PsiElement? = extensionPoint.extensionList.firstNotNullOfOrNull { it.createElement(node) }
  }
}
