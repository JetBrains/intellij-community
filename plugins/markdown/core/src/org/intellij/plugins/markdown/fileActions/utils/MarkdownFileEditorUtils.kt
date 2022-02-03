// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.utils

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor.PREVIEW_BROWSER
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
object MarkdownFileEditorUtils {
  @JvmStatic
  fun findMarkdownPreviewEditor(editor: FileEditor): MarkdownPreviewFileEditor? {
    return when (editor) {
      is MarkdownEditorWithPreview -> editor.previewEditor as? MarkdownPreviewFileEditor
      is MarkdownPreviewFileEditor -> editor
      else -> editor.getUserData(MarkdownEditorWithPreview.PARENT_SPLIT_EDITOR_KEY)?.previewEditor as? MarkdownPreviewFileEditor
    }
  }

  @JvmStatic
  fun findMarkdownPreviewEditor(project: Project, file: VirtualFile, requireOpenedPreview: Boolean): MarkdownPreviewFileEditor? {
    val editor = findEditor(project, file, MarkdownFileEditorUtils::findMarkdownPreviewEditor)
    if (requireOpenedPreview && editor?.getUserData(PREVIEW_BROWSER) == null) {
      return null
    }
    return editor
  }

  @JvmStatic
  fun findMarkdownSplitEditor(editor: FileEditor): MarkdownEditorWithPreview? {
    return when (editor) {
      is MarkdownEditorWithPreview -> editor
      else -> editor.getUserData(MarkdownEditorWithPreview.PARENT_SPLIT_EDITOR_KEY)
    }
  }

  @JvmStatic
  fun findMarkdownSplitEditor(project: Project, file: VirtualFile): MarkdownEditorWithPreview? {
    return findEditor(project, file, ::findMarkdownSplitEditor)
  }

  private fun <T: FileEditor> findEditor(project: Project, file: VirtualFile, predicate: (FileEditor) -> T?): T? {
    val editorManager = FileEditorManager.getInstance(project)
    val selectedEditor = editorManager.getSelectedEditor(file)?.let(predicate)
    if (selectedEditor != null) {
      return selectedEditor
    }
    val editors = editorManager.getEditors(file)
    for (editor in editors) {
      val previewEditor = predicate.invoke(editor)
      if (previewEditor != null) {
        return previewEditor
      }
    }
    return null
  }
}
