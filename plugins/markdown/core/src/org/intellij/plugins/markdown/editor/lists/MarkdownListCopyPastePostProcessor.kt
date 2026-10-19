// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewUtils
import org.intellij.plugins.markdown.lang.supportsMarkdown
import java.awt.datatransfer.Transferable

internal class MarkdownListCopyPastePostProcessor : CopyPastePostProcessor<TextBlockTransferableData>() {
  override fun collectTransferableData(
    file: PsiFile,
    editor: Editor,
    startOffsets: IntArray,
    endOffsets: IntArray,
  ): List<TextBlockTransferableData> = emptyList()

  override fun extractTransferableData(content: Transferable): List<TextBlockTransferableData> =
    listOf(TextBlockTransferableData { null })

  override fun processTransferableData(
    project: Project,
    editor: Editor,
    bounds: RangeMarker,
    caretOffset: Int,
    indented: Ref<in Boolean>,
    values: List<TextBlockTransferableData>,
  ) {
    if (indented.get() != null || !editor.supportsMarkdown()) return
    if (containsIndentedListItem(editor.document.getText(bounds.textRange))) {
      indented.set(true)
    }
  }

  override fun requiresAllDocumentsToBeCommitted(editor: Editor, project: Project): Boolean = false

  private fun containsIndentedListItem(text: String): Boolean = text.lineSequence().any { line ->
    val markerStart = line.indexOfFirst { it != ' ' && it != '\t' }
    markerStart > 0 && isListMarker(line, markerStart)
  }

  private fun isListMarker(line: String, start: Int): Boolean =
    MarkdownLivePreviewUtils.getListMarkerEnd(line, start) != start
}
