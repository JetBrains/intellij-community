// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.injection

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.hasMarkdownType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.ui.MarkdownNotifications
import org.intellij.plugins.markdown.util.MarkdownPluginScope

internal class MarkdownCodeFenceErrorHighlightingIntention : IntentionAction {
  class CodeAnalyzerRestartListener: MarkdownSettings.ChangeListener {
    override fun settingsChanged(settings: MarkdownSettings) {
      val project = settings.project
      val editorManager = FileEditorManager.getInstance(project) ?: return
      val codeAnalyzer = DaemonCodeAnalyzer.getInstance(project) ?: return
      val psiManager = PsiManager.getInstance(project)
      val coroutineScope = MarkdownPluginScope.scope(project)
      coroutineScope.launch(Dispatchers.Default) {
        readAction {
          val files = editorManager.openFiles.filter { it.hasMarkdownType() }
          for (file in files) {
            if (!file.isValid) {
              thisLogger().warn("Virtual file $file is not valid")
              continue
            }
            val psi = psiManager.findFile(file) ?: continue
            codeAnalyzer.restart(psi)
          }
        }
      }
    }
  }

  override fun getText(): String = MarkdownBundle.message("markdown.hide.problems.intention.text")

  override fun getFamilyName(): String = text

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean {
    if (psiFile?.fileType != MarkdownFileType.INSTANCE || !MarkdownSettings.getInstance(project).showProblemsInCodeBlocks) {
      return false
    }
    val element = psiFile?.findElementAt(editor?.caretModel?.offset ?: return false) ?: return false
    return PsiTreeUtil.getParentOfType(element, MarkdownCodeFence::class.java) != null
  }

  override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
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
