package org.intellij.plugins.markdown.editor

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CaretModel
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
