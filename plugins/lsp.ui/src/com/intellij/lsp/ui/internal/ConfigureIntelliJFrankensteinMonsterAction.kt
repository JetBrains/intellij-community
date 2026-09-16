// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lsp.ui.internal

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.lsp.ui.ConfigurableLspIntegrationProvider
import com.intellij.lsp.ui.LspUiBundle
import com.intellij.lsp.ui.settings.LspServerConfiguration
import com.intellij.lsp.ui.settings.LspIntegrationSettingsImpl
import com.intellij.lsp.ui.settings.LspServersConfigurable
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.ui.EditorNotifications

private const val SERVER_NAME: String = "IntelliJ Language Server"
private const val STDIO_ARGUMENT: String = "--stdio"
private const val FILE_PATTERNS: String = "*.kt;*.java"
private const val JAVA_OPTIONS_ENV_NAME: String = "IJ_JAVA_OPTIONS"
private const val JAVA_OPTIONS_ENV_VALUE: String = "-Xmx8g"
private const val NOTIFICATION_GROUP_ID: String = "Language Servers"

/**
 * Registers the IntelliJ language server for the current project.
 * The action asks for the server executable, then it writes a ready-to-use configuration into the LSP server settings.
 * A configuration with the same name is removed and its server is stopped before the new configuration is written.
 */
internal class ConfigureIntelliJFrankensteinMonsterAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabled = project != null && !project.isDefault && project.basePath != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val projectPath = project.getBaseDirectories().singleOrNull() ?: return

    val descriptor = FileChooserDescriptorFactory.singleFile()
      .withTitle(LspUiBundle.message("lsp.settings.server.executable.browse"))
    val executable = FileChooser.chooseFile(descriptor, project, null) ?: return

    val lspClientManager = LspClientManager.getInstance(project)
    val previousRemoved = WriteAction.compute<Boolean, Throwable> {
      LspIntegrationSettingsImpl.getInstance(project).servers.removeAll { it.name == SERVER_NAME }
    }
    if (previousRemoved) {
      lspClientManager.stopClients(ConfigurableLspIntegrationProvider::class.java)
    }

    val configuration = LspServerConfiguration(
      name = SERVER_NAME,
      executablePath = executable.path,
      arguments = STDIO_ARGUMENT,
      filePatterns = FILE_PATTERNS,
      initializationOptions = createInitializationOptions(projectPath.url),
    )
    configuration.envVars.set(EnvironmentVariablesData.create(mapOf(JAVA_OPTIONS_ENV_NAME to JAVA_OPTIONS_ENV_VALUE), true))

    WriteAction.run<Throwable> {
      LspIntegrationSettingsImpl.getInstance(project).servers.add(configuration)
    }

    lspClientManager.stopAndRestartClientsIfNeeded(ConfigurableLspIntegrationProvider::class.java)
    EditorNotifications.getInstance(project).updateAllNotifications()
    notifyConfigured(project, projectPath.presentableUrl)
  }

  private fun notifyConfigured(project: Project, projectPath: String) {
    NotificationGroupManager.getInstance()
      .getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(
        LspUiBundle.message("lsp.notification.intellij.server.configured", projectPath, JAVA_OPTIONS_ENV_VALUE),
        NotificationType.INFORMATION,
      )
      .addAction(NotificationAction.createSimpleExpiring(LspUiBundle.message("lsp.notification.action.open.settings")) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, LspServersConfigurable::class.java)
      })
      .notify(project)
  }

  private fun createInitializationOptions(projectPath: String): String {
    val jpsProject = JsonObject().apply {
      addProperty("type", "jps")
      addProperty("path", projectPath)
    }
    val options = JsonObject().apply {
      add("projects", JsonArray().apply { add(jpsProject) })
    }
    return GsonBuilder().setPrettyPrinting().create().toJson(options)
  }
}