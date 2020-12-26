// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting.ner

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.refactoring.suggested.newRange
import com.intellij.refactoring.suggested.oldRange
import com.intellij.refactoring.suggested.range

internal object MarkdownNamedEntitiesDocumentListener : DocumentListener {
  val CHANGED_RANGES = Key.create<Set<TextRange>>("MARKDOWN_DATE_EXTERNAL_ANNOTATOR_CHANGED_RANGES")

  override fun beforeDocumentChange(event: DocumentEvent) {
    if (event.isWholeTextReplaced) {
      return
    }

    val document = event.document
    val changedRange = event.oldRange

    val project = ProjectManager.getInstance().openProjects.firstOrNull { project ->
      project.isInitialized &&
      !project.isDisposed &&
      PsiDocumentManager.getInstance(project).getCachedPsiFile(document) != null
    } ?: return

    val markupModel = DocumentMarkupModel.forDocument(document, project, false) ?: return

    for (highlighter in markupModel.allHighlighters) {
      val highlightedRange = highlighter.range ?: continue
      if (changedRange.intersects(highlightedRange) && highlighter.getUserData(ENTITY_TYPE) != null) {
        markupModel.removeHighlighter(highlighter)
      }
    }
  }

  override fun documentChanged(event: DocumentEvent) {
    val document = event.document

    if (event.isWholeTextReplaced) {
      document.putUserData(CHANGED_RANGES, null)
      return
    }

    val existingRanges = document.getUserData(CHANGED_RANGES) ?: mutableSetOf()
    document.putUserData(CHANGED_RANGES, existingRanges + event.newRange)
  }
}