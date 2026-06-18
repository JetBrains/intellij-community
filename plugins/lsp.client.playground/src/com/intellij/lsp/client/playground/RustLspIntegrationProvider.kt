// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lsp.client.playground

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor
import com.intellij.platform.lsp.api.lsWidget.LspClientWidgetItem
import javax.swing.Icon


class RustLspIntegrationProvider : LspIntegrationProvider {
  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
    if (isRustLspFile(file)) {
      clientStarter.ensureClientStarted(RustLspServerDescriptor(project))
    }
  }

  override fun createWidgetItem(lspClient: LspClient, currentFile: VirtualFile?): LspClientWidgetItem {
    return LspClientWidgetItem(lspClient, currentFile, AllIcons.General.Language)
  }
}


class RustLspServerDescriptor(project: Project) : ProjectWideLspClientDescriptor(project, "Rust") {
  override fun isSupportedFile(file: VirtualFile): Boolean = isRustLspFile(file)

  override fun createCommandLine(): GeneralCommandLine {
    val rustAnalyzerPath = findRustAnalyzerPath()
                           ?: throw ExecutionException(LspClientPlaygroundBundle.message("rust.lsp.executable.not.found"))
    return GeneralCommandLine(rustAnalyzerPath)
  }
}


class RustLspFileType : FileType {
  override fun getName(): String = "Rust"
  override fun getDescription(): String = LspClientPlaygroundBundle.message("rust.filetype.description")
  override fun getDefaultExtension(): String = "rs"
  override fun getIcon(): Icon = AllIcons.General.Language
  override fun isBinary(): Boolean = false
}

private fun isRustLspFile(file: VirtualFile): Boolean = file.extension == "rs"

private fun findRustAnalyzerPath(): String? {
  val explicitPath = System.getProperty("lsp.client.playground.rust.analyzer.path")
  if (!explicitPath.isNullOrBlank()) {
    return explicitPath
  }

  return PathEnvironmentVariableUtil.findFirst("rust-analyzer")?.toString()
}
