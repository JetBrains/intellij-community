package org.intellij.plugins.markdown.lang.parser

import org.intellij.markdown.MarkdownElementType

/**
 * Check [PsiBuilderFillingVisitor] for usage.
 */
internal open class MarkdownCollapsableElementType(
  name: String,
  isToken: Boolean = false
): MarkdownElementType(name, isToken)
