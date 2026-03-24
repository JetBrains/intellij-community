// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.command.completion

import com.intellij.codeInsight.completion.command.commands.ActionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.idea.ActionsBundle
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.parentOfType

class PropertiesMoveLineUpCommandProvider :
  ActionCommandProvider(actionId = "MoveLineUp",
                        synonyms = listOf("Move line up"),
                        presentableName = ActionsBundle.message("action.MoveLineUp.text"),
                        icon = null,
                        priority = -150,
                        previewText = ActionsBundle.message("action.MoveLineUp.description")) {

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    return super.isApplicable(offset, psiFile, editor) && isAtEndOfProperty(offset, psiFile) && !isNeighborMultiline(offset, psiFile, forward = false)
  }
}

class PropertiesMoveLineDownCommandProvider :
  ActionCommandProvider(actionId = "MoveLineDown",
                        synonyms = listOf("Move line down"),
                        presentableName = ActionsBundle.message("action.MoveLineDown.text"),
                        icon = null,
                        priority = -150,
                        previewText = ActionsBundle.message("action.MoveLineDown.description")) {

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    return super.isApplicable(offset, psiFile, editor) && isAtEndOfProperty(offset, psiFile) && !isNeighborMultiline(offset, psiFile, forward = true)
  }
}

private fun isAtEndOfProperty(offset: Int, psiFile: PsiFile): Boolean {
  val property = getCommandContext(offset, psiFile)?.parentOfType<Property>() ?: return false
  val text = property.text
  if (text.contains("\n")) return false //multiline, it is better to skip
  if (text.endsWith(" ") &&
      text.trim().endsWith("\\")) return false // broken multiline property
  return offset == property.textRange.endOffset
}

private fun isNeighborMultiline(offset: Int, psiFile: PsiFile, forward: Boolean): Boolean {
  val property = getCommandContext(offset, psiFile)?.parentOfType<Property>() ?: return false
  var sibling = if (forward) property.nextSibling else property.prevSibling
  while (sibling is PsiWhiteSpace) {
    sibling = if (forward) sibling.nextSibling else sibling.prevSibling
  }
  return sibling is Property && sibling.text.contains("\n")
}
