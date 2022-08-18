// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.injection

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.ui.MarkdownNotifications

internal class MarkdownCodeFenceErrorHighlightingIntention : IntentionAction {
  class CodeAnalyzerRestartListener: MarkdownSettings.ChangeListener {
    override fun settingsChanged(settings: MarkdownSettings) {
      val project = settings.project
      val editorManager = FileEditorManager.getInstance(project) ?: return
      val codeAnalyzer = DaemonCodeAnalyzerImpl.getInstance(project) ?: return
      val psiManager = PsiManager.getInstance(project)
      editorManager.openFiles
        .asSequence()
        .filter { FileTypeRegistry.getInstance().isFileOfType(it, MarkdownFileType.INSTANCE) }
        .mapNotNull(psiManager::findFile)
        .forEach(codeAnalyzer::restart)
    }
  }

  override fun getText(): String = MarkdownBundle.message("markdown.hide.problems.intention.text")

  override fun getFamilyName(): String = text

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    if (file?.fileType != MarkdownFileType.INSTANCE || !MarkdownSettings.getInstance(project).showProblemsInCodeBlocks) {
      return false
    }
    val element = file?.findElementAt(editor?.caretModel?.offset ?: return false) ?: return false
    return PsiTreeUtil.getParentOfType(element, MarkdownCodeFence::class.java) != null
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    setHideErrors(project, true)
    val notification = MarkdownNotifications.group.createNotification(
      MarkdownBundle.message("markdown.hide.problems.notification.title"),
      MarkdownBundle.message("markdown.hide.problems.notification.content"),
      NotificationType.INFORMATION
    )
    notification.addAction(object: NotificationAction(MarkdownBundle.message("markdown.hide.problems.notification.rollback.action.text")) {
      override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        setHideErrors(project, false)
        notification.expire()
      }
    })
    notification.notify(project)
  }

  private fun setHideErrors(project: Project, hideErrors: Boolean) {
    MarkdownSettings.getInstance(project).update {
      it.showProblemsInCodeBlocks = !hideErrors
    }
  }

  override fun startInWriteAction(): Boolean = false
}
