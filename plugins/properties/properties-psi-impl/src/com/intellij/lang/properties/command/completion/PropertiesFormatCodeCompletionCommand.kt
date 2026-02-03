package com.intellij.lang.properties.command.completion

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.codeInsight.completion.command.commands.AbstractFormatCodeCompletionCommand
import com.intellij.codeInsight.completion.command.commands.AbstractFormatCodeCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parents

class PropertiesFormatCodeCompletionCommandProvider : AbstractFormatCodeCompletionCommandProvider() {
  override fun createCommand(context: CommandCompletionProviderContext): CompletionCommand? {
    val element = getCommandContext(context.offset, context.psiFile) ?: return null
    val targetElement = findTargetToRefactorInner(element)
    val highlightInfoLookup = if (targetElement !is PsiFile)
      HighlightInfoLookup(targetElement.textRange, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)
    else null
    val command = object : PropertiesFormatCodeCompletionCommand(context) {
      override val highlightInfo: HighlightInfoLookup?
        get() {
          return highlightInfoLookup
        }
    }
    return command
  }
}

private fun findTargetToRefactorInner(element: PsiElement): PsiElement {
  val parentElement = (element.parents(true).firstOrNull {
    it is Property && element.textRange.endOffset == it.textRange.endOffset
  } ?: element.containingFile ?: element)
  return parentElement
}

internal abstract class PropertiesFormatCodeCompletionCommand(context: CommandCompletionProviderContext) : AbstractFormatCodeCompletionCommand(context) {
  override fun findTargetToRefactor(element: PsiElement): PsiElement {
    return findTargetToRefactorInner(element)
  }
}