// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.command.completion

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.codeInsight.completion.command.commands.ActionCommandProvider
import com.intellij.codeInsight.completion.command.commands.ActionCompletionCommand
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.idea.ActionsBundle
import com.intellij.lang.LanguageCommenters
import com.intellij.lang.properties.PropertiesBundle
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType

class PropertiesCommentPropertyCommandProvider :
  ActionCommandProvider(actionId = "CommentByLineComment",
                        synonyms = listOf("Comment property", "Toggle comment"),
                        presentableName = PropertiesBundle.message("command.completion.comment.property.text"),
                        icon = null,
                        priority = -100,
                        previewText = ActionsBundle.message("action.CommentByLineComment.description")) {

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (editor == null || offset - 1 < 0) return false
    val commenter = LanguageCommenters.INSTANCE.forLanguage(psiFile.language) ?: return false
    if (commenter.lineCommentPrefix == null) return false
    return isAtEndOfProperty(offset, psiFile)
  }

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    val element = getCommandContext(context.offset, context.psiFile) ?: return null
    val property = element.parentOfType<Property>() ?: return null
    val range = property.textRange

    return object : ActionCompletionCommand(actionId = actionId,
                                            presentableActionName = presentableName,
                                            icon = icon,
                                            priority = priority,
                                            previewText = previewText,
                                            synonyms = synonyms,
                                            highlightInfo = HighlightInfoLookup(range, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)) {
      override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
        if (editor == null) return
        val selectionModel = editor.selectionModel
        if (!highlight(offset, psiFile, selectionModel)) return
        super.execute(offset, psiFile, editor)
        selectionModel.removeSelection()
      }

      private fun highlight(offset: Int, psiFile: PsiFile, selectionModel: SelectionModel): Boolean {
        val ctx = getCommandContext(offset, psiFile) ?: return false
        val prop = ctx.parentOfType<Property>() ?: return false
        selectionModel.setSelection(prop.textRange.startOffset, prop.textRange.endOffset)
        return true
      }
    }
  }
}

private fun isAtEndOfProperty(offset: Int, psiFile: PsiFile): Boolean {
  val element = getCommandContext(offset, psiFile) ?: return false
  val property = element.parentOfType<Property>() ?: return false
  val text = property.text
  if (text.endsWith(" ") && text.trim().endsWith("\\")) return false // broken multiline property
  return offset == property.textRange.endOffset
}
