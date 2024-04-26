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

private class DumpDictionaryToFileAction : AnAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = ActionsLanguageModel.getInstance()?.deferredDictionary?.isCompleted ?: false
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!

    val dictionaryWords = ActionsLanguageModel.getInstance()!!
      .deferredDictionary.getCompleted()
      .allWords
      .sorted()
      .joinToString(separator = "\n")
    val virtualFile = writeToFile(project, dictionaryWords)
    FileEditorManager.getInstance(project).openFile(virtualFile, true)
  }

  fun writeToFile(project: Project, content: CharSequence): VirtualFile {
    return LightVirtualFile("actions-dictionary.txt", PlainTextLanguage.INSTANCE, content)
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