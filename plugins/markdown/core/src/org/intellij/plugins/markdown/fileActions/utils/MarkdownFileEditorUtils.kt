// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.fileActions.utils

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor.Companion.PREVIEW_BROWSER
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
internal object MarkdownFileEditorUtils {
  @JvmStatic
  fun findMarkdownPreviewEditor(editor: FileEditor): MarkdownPreviewFileEditor? {
    return when (editor) {
      is MarkdownEditorWithPreview -> editor.previewEditor as? MarkdownPreviewFileEditor
      is MarkdownPreviewFileEditor -> editor
      else -> TextEditorWithPreview.getParentSplitEditor(editor)?.previewEditor as? MarkdownPreviewFileEditor
    }
  }

  @JvmStatic
  fun findMarkdownPreviewEditor(project: Project, file: VirtualFile, requireOpenedPreview: Boolean): MarkdownPreviewFileEditor? {
    val editor = findEditor(project, file, MarkdownFileEditorUtils::findMarkdownPreviewEditor)
    if (requireOpenedPreview && editor?.getUserData(PREVIEW_BROWSER)?.get() == null) {
      return null
    }
    return editor
  }

  private fun <T: FileEditor> findEditor(project: Project, file: VirtualFile, predicate: (FileEditor) -> T?): T? {
    val editorManager = FileEditorManager.getInstance(project)
    val selectedEditor = editorManager.getSelectedEditor(file)?.let(predicate)
    if (selectedEditor != null) {
      return selectedEditor
    }
    for (editor in editorManager.getEditorList(file)) {
      val previewEditor = predicate.invoke(editor)
      if (previewEditor != null) {
        return previewEditor
      }
    }
    return null
  }
}
