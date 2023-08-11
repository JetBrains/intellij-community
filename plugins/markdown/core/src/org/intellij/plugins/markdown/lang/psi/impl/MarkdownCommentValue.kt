package org.intellij.plugins.markdown.lang.psi.impl

import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class MarkdownCommentValue(text: CharSequence): MarkdownLeafPsiElement(MarkdownElementTypes.COMMENT_VALUE, text)
