package com.intellij.searchEverywhereMl.typos.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.searchEverywhereMl.typos.models.ActionsLanguageModel
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.DocumentUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi

internal class CorpusToFileAction : AnAction() {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = ActionsLanguageModel.getInstance()?.deferredSharedIndex?.isCompleted ?: false
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val corpusSentences = ActionsLanguageModel.getInstance()!!
      .deferredSharedIndex.getCompleted()
      .sentenceTexts()
      .joinToString(separator = "\n")

    val virtualFile = writeToFile(project, corpusSentences)
    FileEditorManager.getInstance(project).openFile(virtualFile, true)
  }

  private fun writeToFile(project: Project, content: CharSequence): VirtualFile {
    return LightVirtualFile("corpus-sentences.txt", PlainTextLanguage.INSTANCE, content)
      .reformatFileContent(project)
      .apply { isWritable = false }
  }

  private fun VirtualFile.reformatFileContent(project: Project) = apply {
    PsiManager.getInstance(project)
      .findFile(this)
      ?.let {
        DocumentUtil.writeInRunUndoTransparentAction {
          CodeStyleManager.getInstance(project).reformat(it, true)
        }
      }
  }
}
