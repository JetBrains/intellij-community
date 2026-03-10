package com.intellij.lang.properties.command.completion

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.commands.ActionCommandProvider
import com.intellij.codeInsight.completion.command.commands.ActionCompletionCommand
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.idea.ActionsBundle
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class PropertiesGoToDeclarationOrUsagesActionCompletionCommandProvider :
  ActionCommandProvider(actionId = "GotoDeclaration",
                        synonyms = listOf("Go to declaration or usages", "Find declaration or usages"),
                        presentableName = ActionsBundle.message("action.GotoDeclaration.text"),
                        icon = null,
                        priority = -100,
                        previewText = ActionsBundle.message("action.GotoDeclaration.description")) {

  override fun supportsReadOnly(): Boolean = true
  override fun supportsInjected(): Boolean = true
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    return super.isApplicable(offset, psiFile, editor) && hasToShow(offset, psiFile)
  }

  private fun hasToShow(offset: Int, psiFile: PsiFile): Boolean {
    val context = (getCommandContext(offset, psiFile)) ?: return false
    return context is PropertyKeyImpl
  }

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    return createCommandWithNameIdentifierAndLastAdjusted(context)
  }
}