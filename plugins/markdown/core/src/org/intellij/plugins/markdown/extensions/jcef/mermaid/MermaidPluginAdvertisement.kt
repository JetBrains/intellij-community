package org.intellij.plugins.markdown.extensions.jcef.mermaid

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.util.application
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.ui.MarkdownNotifications
import java.time.Duration

private val mermaidPluginId = PluginId.getId("com.intellij.mermaid")

private const val NOTIFICATION_SHOWN = "Markdown.Mermaid.Last.Shown"
private val IGNORE_DELAY = Duration.ofDays(7).toMillis()

private fun shouldShow(): Boolean {
  val timestamp = PropertiesComponent.getInstance().getLong(NOTIFICATION_SHOWN, 0)
  if (timestamp < 0) {
    return false
  }
  val current = System.currentTimeMillis()
  return current - timestamp > IGNORE_DELAY
}

private fun markDelayed() {
  PropertiesComponent.getInstance().setValue(NOTIFICATION_SHOWN, System.currentTimeMillis().toString())
}

private fun markShown() {
  PropertiesComponent.getInstance().setValue(NOTIFICATION_SHOWN, "-1")
}

internal fun isMermaidPluginInstalled(): Boolean {
  return PluginManager.isPluginInstalled(mermaidPluginId)
}

internal fun advertiseMermaidPlugin(project: Project) {
  if (isMermaidPluginInstalled() || application.isUnitTestMode || !shouldShow()) {
    return
  }
  markDelayed()
  invokeLater {
    val text = MarkdownBundle.message("markdown.notification.mermaid.advertisement.text")
    val remindLaterAction = NotificationAction.createSimpleExpiring(MarkdownBundle.message("markdown.notification.mermaid.advertisement.remind.later.action.text")) {
      markDelayed()
    }
    val ignoreAction = NotificationAction.createSimpleExpiring(MarkdownBundle.message("markdown.notification.mermaid.advertisement.ignore.action.text")) {
      markShown()
    }
    val group = MarkdownNotifications.group
    group.createNotification(text, NotificationType.INFORMATION)
      .setTitle(MarkdownBundle.message("markdown.notification.mermaid.advertisement.title"))
      .addAction(InstallMermaidPluginAction())
      .addAction(remindLaterAction)
      .addAction(ignoreAction)
      .setSuggestionType(true)
      .notify(project)
  }
}

internal fun installMermaidPlugin(project: Project?, onSuccess: () -> Unit = {}) {
  installAndEnable(project, setOf(mermaidPluginId), true, onSuccess = onSuccess)
}

private class InstallMermaidPluginAction: NotificationAction(MarkdownBundle.message("markdown.notification.mermaid.advertisement.install.action.text")) {
  override fun actionPerformed(event: AnActionEvent, notification: Notification) {
    val project = event.project
    installMermaidPlugin(project) {
      notification.expire()
    }
  }
}
