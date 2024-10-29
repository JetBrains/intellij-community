// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables.ui

import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.ex.SoftWrapModelEx
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.util.SlowOperations
import org.intellij.plugins.markdown.lang.MarkdownFileType

/**
 * This factory listener will add soft wrap model listener, so we can update all our inlay hints and potentially disable some of them.
 */
internal class MarkdownInlayUpdateOnSoftWrapListener: EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    SlowOperations.knownIssue("IJPL-162344").use {
      if (!ReadAction.compute<Boolean, Nothing> { isMarkdownEditor(editor) }) return
    }
    val softWrapModel = (editor.softWrapModel as? SoftWrapModelEx) ?: return
    softWrapModel.addSoftWrapChangeListener(object : SoftWrapChangeListener {
      override fun softWrapsChanged() {
        InlayHintsPassFactoryInternal.forceHintsUpdateOnNextPass()
      }

      override fun recalculationEnds() = Unit
    })
  }

  private fun isMarkdownEditor(editor: Editor): Boolean =
    editor.virtualFile != null && FileTypeManager.getInstance().isFileOfType(editor.virtualFile, MarkdownFileType.INSTANCE)
}
