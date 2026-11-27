// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.actions

import com.intellij.ide.TrustedFiles
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.markdown.frontend.highlighting.MarkdownLocalRunLineMarkerContributor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence

internal class MarkdownRunCodeFenceAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = findAction(event) != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    val action = findAction(event) ?: return
    ActionUtil.performAction(action, event)
  }

  private fun findAction(event: AnActionEvent): AnAction? {
    val editor = event.getData(CommonDataKeys.EDITOR) ?: return null
    val file = event.getData(CommonDataKeys.PSI_FILE)?.takeIf { it.isValid } ?: return null
    if (file.textLength == 0) return null
    val virtualFile = file.originalFile.virtualFile
    if (virtualFile != null && !TrustedFiles.isTrusted(virtualFile, file.project)) return null
    val element = file.findElementAt(editor.caretModel.offset.coerceAtMost(file.textLength - 1)) ?: return null
    val host = InjectedLanguageManager.getInstance(element.project).getInjectionHost(element) ?: element
    val fence = PsiTreeUtil.getParentOfType(host, MarkdownCodeFence::class.java, false) ?: return null
    val language = fence.node.findChildByType(MarkdownTokenTypes.FENCE_LANG)?.psi ?: return null
    return MarkdownLocalRunLineMarkerContributor().getInfo(language)?.actions?.singleOrNull()
  }
}
