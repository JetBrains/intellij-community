package com.jetbrains.performancePlugin.utils

import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.openapi.application.readAction
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
  }
}