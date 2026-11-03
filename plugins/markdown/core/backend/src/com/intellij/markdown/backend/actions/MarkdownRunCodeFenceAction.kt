// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.actions

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ExecutionSafeMode
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.markdown.backend.highlighting.markers.MarkdownRunLineMarkersProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence

internal class MarkdownRunCodeFenceAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = findAction(event) != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    val action = findAction(event) ?: return
    ActionUtil.performAction(action, event)
  }

  private fun findAction(event: AnActionEvent): AnAction? {
    val context = ConfigurationContext.getFromContext(event.dataContext, event.place)
    if (!ExecutionSafeMode.isRunAllowed(context)) return null
    val element = context.psiLocation?.takeIf { it.isValid } ?: return null
    val host = InjectedLanguageManager.getInstance(element.project).getInjectionHost(element) ?: element
    val fence = PsiTreeUtil.getParentOfType(host, MarkdownCodeFence::class.java, false) ?: return null
    val language = fence.node.findChildByType(MarkdownTokenTypes.FENCE_LANG)?.psi ?: return null
    return MarkdownRunLineMarkersProvider.getInfo(language)?.actions?.singleOrNull()
  }
}
