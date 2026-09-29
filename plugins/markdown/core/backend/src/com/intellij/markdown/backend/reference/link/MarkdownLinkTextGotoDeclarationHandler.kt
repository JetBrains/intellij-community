// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.reference.link

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.parentOfTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLink
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkText

internal class MarkdownLinkTextGotoDeclarationHandler : GotoDeclarationHandler {
  override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
    val linkText = sourceElement?.parentOfTypes(MarkdownLinkText::class, MarkdownLinkDestination::class) as? MarkdownLinkText ?: return null
    val destination = (linkText.parent as? MarkdownLink)?.linkDestination ?: return null
    val valueRange = ElementManipulators.getValueTextRange(destination)
    if (valueRange.isEmpty) return null
    val reference = destination.findReferenceAt(valueRange.endOffset - 1) ?: return null
    val targets = when (reference) {
      is PsiPolyVariantReference -> reference.multiResolve(false).mapNotNull { it.element }
      else -> listOfNotNull(reference.resolve())
    }
    return targets.toTypedArray()
  }
}
