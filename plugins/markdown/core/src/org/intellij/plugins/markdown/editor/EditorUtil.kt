package org.intellij.plugins.markdown.editor

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.lang.isMarkdownLanguage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun CaretModel.runForEachCaret(reverseOrder: Boolean = false, block: (Caret) -> Unit) {
  runForEachCaret(block, reverseOrder)
}

@ApiStatus.Internal
fun isMarkdownScratchFile(project: Project, file: VirtualFile): Boolean {
  if (!ScratchUtil.isScratch(file)) {
    return false
  }
  val language = LanguageUtil.getLanguageForPsi(project, file) ?: return false
  return language.isMarkdownLanguage()
}

@ApiStatus.Internal
fun findFirstOpenEditorForFile(project: Project, file: VirtualFile): Editor? {
  val fileEditorManager = FileEditorManager.getInstance(project)
  val fileEditors = fileEditorManager.getEditors(file)

  for (fileEditor in fileEditors) {
    if (fileEditor is TextEditor && fileEditor.editor.contentComponent.isShowing) {
      return fileEditor.editor
    }
  }

  return null
}
