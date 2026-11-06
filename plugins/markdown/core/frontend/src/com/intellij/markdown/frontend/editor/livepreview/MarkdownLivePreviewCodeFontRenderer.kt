// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.toTextRange

/** Shows the code ranges of the [MarkdownLivePreviewSpec.CodeFont] specs in JetBrains Mono. A revealed element keeps the font. */
internal class MarkdownLivePreviewCodeFontRenderer(private val editor: Editor) : MarkdownLivePreviewElementRenderer {
  private val codeFont = createCodeFont()
  private var highlighters = emptyList<RangeHighlighter>()

  override fun presentation(spec: MarkdownLivePreviewSpec): List<MarkdownLivePreviewFold> = emptyList()

  override fun documentChanged() = Unit

  override fun reconcile(presentation: MarkdownLivePreviewPresentation?) {
    val missing = LinkedHashSet<TextRange>()
    for (element in presentation?.elements.orEmpty()) {
      val spec = element.spec
      if (spec !is MarkdownLivePreviewSpec.CodeFont) continue
      for (range in spec.codeRanges) {
        missing.add(range.toTextRange())
      }
    }
    val kept = ArrayList<RangeHighlighter>()
    for (highlighter in highlighters) {
      if (highlighter.isValid && missing.remove(highlighter.textRange)) {
        kept.add(highlighter)
      }
      else {
        highlighter.dispose()
      }
    }
    for (range in missing) {
      kept.add(addHighlighter(range))
    }
    highlighters = kept
  }

  private fun addHighlighter(range: TextRange): RangeHighlighter {
    val highlighter = editor.markupModel.addRangeHighlighter(
      range.startOffset, range.endOffset, HighlighterLayer.ADDITIONAL_SYNTAX + 1, codeFont, HighlighterTargetArea.EXACT_RANGE,
    )
    highlighter.isGreedyToLeft = true
    highlighter.isGreedyToRight = true
    return highlighter
  }

  override fun dispose() {
    for (highlighter in highlighters) {
      highlighter.dispose()
    }
    highlighters = emptyList()
  }
}

private fun createCodeFont(): TextAttributes {
  val attributes = TextAttributes()
  attributes.fontFamilies = FontFamilies.of("JetBrains Mono")
  return attributes
}
