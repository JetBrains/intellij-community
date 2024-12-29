package com.intellij.restructuredtext.python.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.Companion.getInstance
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.restructuredtext.editor.RestEditorProvider

private class RestSplitEditorProvider : RestEditorProvider() {
  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val editor: TextEditor = getInstance().createEditor(project, file) as TextEditor
    return TextEditorWithPreview(editor, RestPreviewFileEditor(file, project))
  }

  override fun getPolicy() = FileEditorPolicy.HIDE_OTHER_EDITORS
}