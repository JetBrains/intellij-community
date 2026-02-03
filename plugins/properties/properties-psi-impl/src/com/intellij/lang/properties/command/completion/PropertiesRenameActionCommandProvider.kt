package com.intellij.lang.properties.command.completion

import com.intellij.codeInsight.completion.command.commands.AbstractRenameActionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.lang.properties.psi.Property
import com.intellij.lang.properties.psi.impl.PropertyImpl
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType

class PropertiesRenameActionCommandProvider : AbstractRenameActionCommandProvider() {
  override fun findRenameOffset(offset: Int, psiFile: PsiFile): Int? {
    val element = getCommandContext(offset, psiFile)?.parentOfType<Property>() as? PropertyImpl ?: return null
    val nameIdentifier = element.nameIdentifier
    if (nameIdentifier != null && offset == nameIdentifier.textRange.endOffset) return nameIdentifier.textRange.endOffset
    return null
  }
}