// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.PsiBasedModCommandAction
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence

internal class CopyCodeFenceContentIntention : PsiBasedModCommandAction<MarkdownCodeFence>(MarkdownCodeFence::class.java) {
  override fun getFamilyName(): String = MarkdownBundle.message("markdown.copy.code.fence.content.intention.text")

  override fun perform(context: ActionContext, element: MarkdownCodeFence): ModCommand {
    val escaper = element.createLiteralTextEscaper()
    val content = StringBuilder()
    escaper.decode(escaper.relevantTextRange, content)
    return ModCommand.copyToClipboard(content.toString())
  }
}
