// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lsp.ui

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.lsp.ui.settings.LspServerConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspCommunicationChannel
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor

internal class ConfigurableLspClientDescriptor(
  project: Project,
  configuration: LspServerConfiguration,
) : ProjectWideLspClientDescriptor(project, configuration.name) {

  private val configuration: LspServerConfiguration = configuration.copy()

  override fun isSupportedFile(file: VirtualFile): Boolean {
    if (!configuration.enabled) {
      return false
    }

    val matchers = configuration.getFileMatchers()
    for (matcher in matchers) {
      if (matcher.acceptsCharSequence(file.name)) {
        return true
      }
    }

    return false
  }

  override fun createCommandLine(): GeneralCommandLine {
    if (configuration.executablePath.isEmpty()) {
      throw ExecutionException(LspUiBundle.message("lsp.settings.empty.executable.dialog", configuration.name))
    }

    val commandLine = GeneralCommandLine(configuration.executablePath)

    configuration.getArgumentsList().forEach { arg ->
      commandLine.addParameter(arg)
    }

    roots.getOrNull(0)?.let { commandLine.withWorkingDirectory(it.toNioPath()) }

    configuration.envVars.get().configureCommandLine(commandLine, true)

    return commandLine
  }

  override val lspCommunicationChannel: LspCommunicationChannel
    get() = when (configuration.communicationMode) {
      LspServerConfiguration.CommunicationMode.STDIO -> LspCommunicationChannel.StdIO
      LspServerConfiguration.CommunicationMode.SOCKET -> LspCommunicationChannel.Socket(
        port = configuration.socketPort,
        startProcess = configuration.executablePath.isNotBlank()
      )
    }

  override fun createInitializationOptions(): Any? {
    if (configuration.initializationOptions.isBlank()) {
      return null
    }

    return try {
      Gson().fromJson(configuration.initializationOptions, Map::class.java)
    } catch (e: JsonSyntaxException) {
      LOG.warn("Invalid JSON in initialization options for '${configuration.name}': ${e.message}")
      null
    }
  }
}