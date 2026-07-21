// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.application.options.CodeStyle
import com.intellij.codeInspection.longLine.LongLineInspectionPolicy
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownAutoLink
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownInlineLink
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDefinition
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownWrappedAutoLink

internal class MarkdownLongLineInspectionPolicy : LongLineInspectionPolicy {
  override fun ignoreLongLineFor(element: PsiElement): Boolean {
    if (PsiTreeUtil.getParentOfType(element, MarkdownTable::class.java, false) != null) return true

    val link = PsiTreeUtil.getParentOfType(
      element,
      false,
      MarkdownLinkDestination::class.java,
      MarkdownInlineLink::class.java,
      MarkdownLinkDefinition::class.java,
      MarkdownAutoLink::class.java,
      MarkdownWrappedAutoLink::class.java,
    ) ?: return false
    val rightMargin = CodeStyle.getSettings(element.containingFile).getRightMargin(element.language)
    return link.textLength > rightMargin
  }
}
