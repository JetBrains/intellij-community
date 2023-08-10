package org.intellij.plugins.markdown.lang.psi.impl

import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class MarkdownTableSeparator(text: CharSequence): MarkdownLeafPsiElement(MarkdownTokenTypes.TABLE_SEPARATOR, text)
