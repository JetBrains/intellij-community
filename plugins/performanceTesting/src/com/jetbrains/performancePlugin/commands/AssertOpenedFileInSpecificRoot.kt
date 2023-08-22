package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls

class AssertOpenedFileInSpecificRoot(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "assertOpenedFileInRoot"
  }
  override suspend fun doExecute(context: PlaybackContext) {
    withContext(Dispatchers.EDT) {
      val project = context.project
      val editor = FileEditorManager.getInstance(project).selectedTextEditor
      val file = PsiDocumentManager.getInstance(context.project).getPsiFile(editor!!.document)!!.virtualFile
      val index = ProjectFileIndex.getInstance(project)
      if (!index.isInSource(file) && !index.isInTestSourceContent(file)) {
        throw IllegalStateException("File $file not in test/source root")
      }
    }
  }
}