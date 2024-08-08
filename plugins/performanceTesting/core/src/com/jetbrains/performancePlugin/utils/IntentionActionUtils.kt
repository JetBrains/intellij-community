package com.jetbrains.performancePlugin.utils

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiManager
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.TestOnly

@Suppress("unused")
class IntentionActionUtils {
  companion object {

    @Suppress("SSBasedInspection")
    @TestOnly
    @JvmStatic
    fun invokeIntentionAction(editor: Editor, actionName: String) = runBlocking {
      val project = editor.project ?: throw IllegalStateException("Project is null")
      val psiFile = readAction { PsiManager.getInstance(project).findFile(editor.virtualFile) }
                    ?: throw IllegalStateException("File is null")
      IntentionManager.getInstance().availableIntentions.firstOrNull { it.text == actionName }
        ?.invoke(project, editor, psiFile) ?: throw IllegalStateException("Intention $actionName not found")
    }

    @Suppress("SSBasedInspection")
    @TestOnly
    @JvmStatic
    fun invokeQuickFix(editor: Editor, highlightInfo: HighlightInfo, name: String) = runBlocking {
      val action = checkNotNull(highlightInfo.findRegisteredQuickFix { desc, _ ->
        if (desc.action.text == name) {
          desc.action
        } else {
          null
        }
      }) { "$name quick fix not found" }
      val project = checkNotNull(editor.project) { "Project is null" }
      val psiFile = checkNotNull(readAction { PsiManager.getInstance(project).findFile(editor.virtualFile) }) { "File is null" }
      if (action.startInWriteAction()) {
        writeAction {
          action.invoke(project, editor, psiFile)
        }
      } else {
        action.invoke(project, editor, psiFile)
      }
    }
  }
}