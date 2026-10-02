// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.fence

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.CustomHighlighterOrder
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics

@ApiStatus.Internal
val MARKDOWN_CODE_FENCE_HIGHLIGHTER_ID: String = "IJ." + MarkdownHighlighterColors.CODE_FENCE_BACKGROUND.externalName

@ApiStatus.Internal
class MarkdownCodeFenceBackgroundRenderer : CustomHighlighterRenderer {
  override fun getOrder(): CustomHighlighterOrder = CustomHighlighterOrder.BEFORE_BACKGROUND

  override fun paint(editor: Editor, highlighter: RangeHighlighter, graphics: Graphics) {
    val color = backgroundColor(editor) ?: return
    // offsetToXY maps every offset of a collapsed region to the line of its placeholder. A fence that
    // a region hides completely therefore paints as a single row over that placeholder, so skip it. A
    // fence that a region hides only in part keeps its paint, because such a placeholder is itself a
    // line of the code. An expanded region hides nothing, so it needs no check.
    val collapsed = editor.foldingModel.getCollapsedRegionAtOffset(highlighter.startOffset)
    if (collapsed != null && collapsed.endOffset >= highlighter.endOffset) return
    val left = editor.offsetToXY(highlighter.startOffset).x
    val width = editor.contentComponent.width - left
    if (width <= 0) return
    val openingLine = editor.document.getLineNumber(highlighter.startOffset)
    if (openingLine + 1 >= editor.document.lineCount) return
    val top = editor.offsetToXY(editor.document.getLineStartOffset(openingLine + 1)).y
    val bottom = editor.offsetToXY(highlighter.endOffset).y
    graphics.color = color
    var y = top
    while (y <= bottom) {
      graphics.fillRect(left, y, width, editor.lineHeight)
      y += editor.lineHeight
    }
  }

  private fun backgroundColor(editor: Editor): Color? {
    val scheme = editor.colorsScheme
    scheme.getAttributes(MarkdownHighlighterColors.CODE_FENCE)?.backgroundColor?.let { return it }
    val injectedKey = EditorColors.createInjectedLanguageFragmentKey(MarkdownLanguage.INSTANCE)
    return scheme.getAttributes(injectedKey)?.backgroundColor
  }
}
