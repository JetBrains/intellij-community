// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables.ui

import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.ex.SoftWrapModelEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeManager
import org.intellij.plugins.markdown.lang.MarkdownFileType

/**
 * This factory listener will add soft wrap model listener, so we can update all our inlay hints and potentially disable some of them.
 */
@VisibleForTesting
class MarkdownInlayUpdateOnSoftWrapListener: EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
    if (FileTypeManager.getInstance().getFileTypeByFileName(file.nameSequence) != MarkdownFileType.INSTANCE) return
    val softWrapModel = (editor.softWrapModel as? SoftWrapModelEx) ?: return
    softWrapModel.addSoftWrapChangeListener(object : SoftWrapChangeListener {
      override fun softWrapsChanged() {
        InlayHintsPassFactoryInternal.forceHintsUpdateOnNextPass()
      }

      override fun recalculationEnds() = Unit
    })
  }
}
