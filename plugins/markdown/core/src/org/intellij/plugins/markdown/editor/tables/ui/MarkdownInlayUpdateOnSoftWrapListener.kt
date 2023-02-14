// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.ui

import com.intellij.codeInsight.hints.InlayHintsPassFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.ex.SoftWrapModelEx
import com.intellij.psi.PsiDocumentManager
import org.intellij.plugins.markdown.lang.MarkdownFileType

/**
 * This factory listener will add soft wrap model listener, so we can update
 * all our inlay hints, and potentially disable some of them.
 */
internal class MarkdownInlayUpdateOnSoftWrapListener: EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    if (isMarkdownEditor(editor)) {
      val softWrapModel = (editor.softWrapModel as? SoftWrapModelEx) ?: return
      softWrapModel.addSoftWrapChangeListener(object : SoftWrapChangeListener {
        override fun softWrapsChanged() {
          InlayHintsPassFactory.forceHintsUpdateOnNextPass()
        }

        override fun recalculationEnds() = Unit
      })
    }
  }

  companion object {
    private fun isMarkdownEditor(editor: Editor): Boolean {
      val project = editor.project ?: return false
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return false
      return psiFile.fileType == MarkdownFileType.INSTANCE
    }
  }
}
