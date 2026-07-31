// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lsp.ui

import com.intellij.lsp.ui.settings.LspServerSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserSuppressor
import com.intellij.openapi.vfs.VirtualFile

internal class LspConfiguredPluginAdvertiserSuppressor : PluginAdvertiserSuppressor {
  override fun isSuppressedFor(project: Project, file: VirtualFile): Boolean {
    return LspServerSettings.getInstance(project).servers.any { configuration ->
      configuration.enabled && configuration.getFileMatchers().any { it.acceptsCharSequence(file.name) }
    }
  }
}
