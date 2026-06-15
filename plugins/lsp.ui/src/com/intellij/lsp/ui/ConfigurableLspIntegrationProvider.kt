// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lsp.ui

import com.intellij.lsp.ui.settings.LspServerSettings
import com.intellij.lsp.ui.settings.LspServersConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.lsWidget.LspClientWidgetItem

internal class ConfigurableLspIntegrationProvider : LspIntegrationProvider {
  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
    val settings = LspServerSettings.getInstance(project)
    for (configuration in settings.servers) {
      if (!configuration.enabled) {
        continue
      }
      val descriptor = ConfigurableLspClientDescriptor(project, configuration)
      if (descriptor.isSupportedFile(file)) {
        clientStarter.ensureClientStarted(descriptor)
      }
    }
  }

  override fun createWidgetItem(lspClient: LspClient, currentFile: VirtualFile?): LspClientWidgetItem =
    LspClientWidgetItem(lspClient, currentFile, settingsPageClass = LspServersConfigurable::class.java)
}