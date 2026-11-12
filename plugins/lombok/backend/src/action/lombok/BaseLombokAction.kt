// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lombok.backend.action.lombok

import com.intellij.codeInsight.generation.OverrideImplementUtil.getContextClass
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.annotations.Nls

abstract class BaseLombokAction(
  private val modActionFactory: (PsiClass) -> ModCommandAction,
) : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val context = e.toModCommandContext()
    val editor = e.getData(CommonDataKeys.EDITOR)
    val modAction = if (context != null && editor != null) createModAction(context, editor) else null
    e.presentation.setEnabled(context != null && modAction != null && modAction.getPresentation(context) != null)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val context = e.toModCommandContext() ?: return
    ModCommandExecutor.executeInteractively(context, e.presentation.text ?: getActionText(e), editor) {
      createModAction(context, editor)?.perform(context) ?: ModCommand.nop()
    }
  }

  protected abstract fun getActionText(e: AnActionEvent): @Nls String

  private fun createModAction(context: ActionContext, editor: Editor): ModCommandAction? {
    val psiFile = context.file
    if (psiFile !is PsiJavaFile) return null
    if (!psiFile.isWritable) return null
    val psiClass = getContextClass(context.project, editor, psiFile, false) ?: return null
    return modActionFactory(psiClass)
  }

  private fun AnActionEvent.toModCommandContext(): ActionContext? {
    val project = project ?: return null
    val editor = getData(CommonDataKeys.EDITOR) ?: return null
    val file = PsiUtilBase.getPsiFileInEditor(editor, project) ?: return null
    return ActionContext.from(editor, file)
  }
}
