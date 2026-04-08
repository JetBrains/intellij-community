// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.command.completion

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.codeInsight.completion.command.commands.ActionCommandProvider
import com.intellij.codeInsight.completion.command.commands.ActionCompletionCommand
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.idea.ActionsBundle
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType

class PropertiesMovePropertyUpCommandProvider :
  ActionCommandProvider(actionId = "MoveStatementUp",
                        synonyms = listOf("Move property up"),
                        presentableName = ActionsBundle.message("action.MoveStatementUp.text"),
                        icon = null,
                        priority = -150,
                        previewText = ActionsBundle.message("action.MoveStatementUp.description")) {

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    return super.isApplicable(offset, psiFile, editor) && isAtEndOfProperty(offset, psiFile)
  }

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    val range = getPropertyRange(context) ?: return super.createCommand(context)
    return ActionCompletionCommand(actionId = actionId,
                                   presentableActionName = presentableName,
                                   icon = icon,
                                   priority = priority,
                                   previewText = previewText,
                                   synonyms = synonyms,
                                   highlightInfo = HighlightInfoLookup(range, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0))
  }
}

class PropertiesMovePropertyDownCommandProvider :
  ActionCommandProvider(actionId = "MoveStatementDown",
                        synonyms = listOf("Move property down"),
                        presentableName = ActionsBundle.message("action.MoveStatementDown.text"),
                        icon = null,
                        priority = -150,
                        previewText = ActionsBundle.message("action.MoveStatementDown.description")) {

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    return super.isApplicable(offset, psiFile, editor) && isAtEndOfProperty(offset, psiFile)
  }

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    val range = getPropertyRange(context) ?: return super.createCommand(context)
    return ActionCompletionCommand(actionId = actionId,
                                   presentableActionName = presentableName,
                                   icon = icon,
                                   priority = priority,
                                   previewText = previewText,
                                   synonyms = synonyms,
                                   highlightInfo = HighlightInfoLookup(range, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0))
  }
}

private fun isAtEndOfProperty(offset: Int, psiFile: PsiFile): Boolean {
  val property = getCommandContext(offset, psiFile)?.parentOfType<Property>() ?: return false
  val text = property.text
  if (text.endsWith(" ") &&
      text.trim().endsWith("\\")) return false // broken multiline property
  return offset == property.textRange.endOffset
}

private fun getPropertyRange(context: CommandCompletionProviderContext): TextRange? {
  val property = getCommandContext(context.offset, context.psiFile)?.parentOfType<Property>() ?: return null
  return property.textRange
}
