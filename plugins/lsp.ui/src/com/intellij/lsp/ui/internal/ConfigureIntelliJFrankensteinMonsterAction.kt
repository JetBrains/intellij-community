// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lsp.ui.internal

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.lsp.ui.ConfigurableLspIntegrationProvider
import com.intellij.lsp.ui.LspUiBundle
import com.intellij.lsp.ui.settings.LspServerConfiguration
import com.intellij.lsp.ui.settings.LspServerSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.ui.EditorNotifications

private const val SERVER_NAME: String = "IntelliJ LSP Server"
private const val STDIO_ARGUMENT: String = "--stdio"
private const val FILE_PATTERNS: String = "*.kt;*.java"
private const val JAVA_OPTIONS_ENV_NAME: String = "IJ_JAVA_OPTIONS"
private const val JAVA_OPTIONS_ENV_VALUE: String = "-Xmx8g"

/**
 * Registers the IntelliJ language server for the current project.
 * The action asks for the server executable, then it writes a ready-to-use configuration into the LSP server settings.
 */
internal class ConfigureIntelliJFrankensteinMonsterAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabled = project != null && !project.isDefault && project.basePath != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val projectPath = project.basePath ?: return

    val descriptor = FileChooserDescriptorFactory.singleFile()
      .withTitle(LspUiBundle.message("lsp.settings.server.executable.browse"))
    val executable = FileChooser.chooseFile(descriptor, project, null) ?: return

    val configuration = LspServerConfiguration(
      name = SERVER_NAME,
      executablePath = executable.url,
      arguments = STDIO_ARGUMENT,
      filePatterns = FILE_PATTERNS,
      initializationOptions = createInitializationOptions(projectPath),
    )
    configuration.envVars.set(EnvironmentVariablesData.create(mapOf(JAVA_OPTIONS_ENV_NAME to JAVA_OPTIONS_ENV_VALUE), true))

    WriteAction.run<Throwable> {
      storeConfiguration(project, configuration)
    }

    LspClientManager.getInstance(project).stopAndRestartClientsIfNeeded(ConfigurableLspIntegrationProvider::class.java)
    EditorNotifications.getInstance(project).updateAllNotifications()
  }

  private fun storeConfiguration(project: Project, configuration: LspServerConfiguration) {
    val servers = LspServerSettings.getInstance(project).servers
    val index = servers.indexOfFirst { it.name == configuration.name }
    if (index >= 0) {
      servers[index] = configuration
    }
    else {
      servers.add(configuration)
    }
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