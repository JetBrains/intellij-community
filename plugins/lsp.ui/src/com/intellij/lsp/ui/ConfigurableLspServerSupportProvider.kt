// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lsp.ui

import com.intellij.lsp.ui.settings.LspServerSettings
import com.intellij.lsp.ui.settings.LspServersConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem

internal class ConfigurableLspServerSupportProvider : LspServerSupportProvider {
  override fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerSupportProvider.LspServerStarter) {
    val settings = LspServerSettings.getInstance(project)
    for (configuration in settings.servers) {
      if (!configuration.enabled) {
        continue
      }
      val descriptor = ConfigurableLspServerDescriptor(project, configuration)
      if (descriptor.isSupportedFile(file)) {
        serverStarter.ensureServerStarted(descriptor)
      }
    }
  }

  override fun createLspServerWidgetItem(lspServer: LspServer, currentFile: VirtualFile?): LspServerWidgetItem =
    LspServerWidgetItem(lspServer, currentFile, settingsPageClass = LspServersConfigurable::class.java)
}