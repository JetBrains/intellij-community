// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.prompt

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.ui.util.preferredWidth
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.terminal.exp.HighlightingInfo
import org.jetbrains.plugins.terminal.exp.TerminalUi
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle

/**
 * @param promptEditor the editor used for showing prompt and command input
 */
internal class RightPromptManager(private val promptEditor: Editor,
                                  settings: JBTerminalSystemSettingsProviderBase) : Disposable {
  /** Editor used as a renderer for the right prompt text */
  private val inlayEditor: EditorImpl = EditorFactory.getInstance().createEditor(DocumentImpl("")) as EditorImpl

  private var inlay: Inlay<*>? = null

  init {
    inlayEditor.isViewer = true
    inlayEditor.settings.isCaretRowShown = false
    inlayEditor.colorsScheme.apply {
      editorFontName = settings.terminalFont.fontName
      editorFontSize = settings.terminalFont.size
      lineSpacing = 1.0f
    }
  }

  @RequiresEdt
  fun update(commandStartOffset: Int, text: String, highlightings: List<HighlightingInfo>) {
    updateInlayEditor(text, highlightings)
    inlay?.let { Disposer.dispose(it) }
    inlay = promptEditor.inlayModel.addAfterLineEndElement(commandStartOffset, false, RightPromptRenderer(inlayEditor))
  }

  private fun updateInlayEditor(text: String, highlightings: List<HighlightingInfo>) {
    DocumentUtil.writeInRunUndoTransparentAction {
      inlayEditor.document.setText(text)
    }
    inlayEditor.markupModel.removeAllHighlighters()
    for (highlighting in highlightings) {
      inlayEditor.markupModel.addRangeHighlighter(highlighting.startOffset, highlighting.endOffset, HighlighterLayer.SYNTAX,
                                                  highlighting.textAttributesProvider.getTextAttributes(), HighlighterTargetArea.EXACT_RANGE)
    }
  }

  override fun dispose() {
    EditorFactory.getInstance().releaseEditor(inlayEditor)
    inlay?.let { Disposer.dispose(it) }
  }

  private class RightPromptRenderer(private val inlayEditor: Editor) : EditorCustomElementRenderer {
    /**
     * Return fake width here, to make sure that editor is not taking this inlay into account during text layout.
     * Because it is intended to be painted only when it does not intersect with user input.
     */
    override fun calcWidthInPixels(inlay: Inlay<*>): Int = 1

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
      val textWidth = inlayEditor.contentComponent.preferredWidth
      val visibleArea = inlay.editor.scrollingModel.visibleArea
      val textX = visibleArea.x + visibleArea.width - textWidth - JBUI.scale(TerminalUi.cornerToBlockInset)

      val promptEditor = inlay.editor
      val promptLine = promptEditor.offsetToLogicalPosition(inlay.offset).line
      val textY = promptLine * inlayEditor.lineHeight
      val lineStartOffset = promptEditor.document.getLineStartOffset(promptLine)
      val lineEndOffset = promptEditor.document.getLineEndOffset(promptLine)
      val textPosition = promptEditor.xyToLogicalPosition(Point(textX, textY))
      // paint the right prompt only if the user input does not occupy the right prompt place
      if (lineEndOffset - lineStartOffset < textPosition.column) {
        val g2 = g.create()
        try {
          doPaint(g2, textX, textY, textWidth, targetRegion.height)
        }
        finally {
          g2.dispose()
        }
      }
    }

    private fun doPaint(g: Graphics, x: Int, y: Int, width: Int, height: Int) {
      g.translate(x, y)
      g.clipRect(0, 0, width, height)
      inlayEditor.contentComponent.paint(g)
    }
  }
}