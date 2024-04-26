package org.intellij.plugins.markdown.lang.parser.blocks

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType
import org.intellij.markdown.MarkdownElementTypes
import org.jetbrains.annotations.ApiStatus
import org.intellij.plugins.markdown.lang.MarkdownElementTypes as MarkdownParserElementTypes

@get:ApiStatus.Experimental
val MarkdownElementTypes.COMMENT: IElementType
  get() = MarkdownParserElementTypes.COMMENT

private val commentValue = MarkdownElementType("COMMENT_VALUE", true)

@get:ApiStatus.Experimental
val MarkdownElementTypes.COMMENT_VALUE: IElementType
  get() = commentValue
