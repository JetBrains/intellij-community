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


class SwiftLspIntegrationProvider : LspIntegrationProvider {
  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
    if (isSwiftLspFile(file)) {
      clientStarter.ensureClientStarted(SwiftLspServerDescriptor(project))
    }
  }

  override fun createWidgetItem(lspClient: LspClient, currentFile: VirtualFile?): LspClientWidgetItem {
    return LspClientWidgetItem(lspClient, currentFile, AllIcons.General.Language)
  }
}


class SwiftLspServerDescriptor(project: Project) : ProjectWideLspClientDescriptor(project, "Swift") {
  override fun isSupportedFile(file: VirtualFile): Boolean = isSwiftLspFile(file)

  override fun createCommandLine(): GeneralCommandLine {
    val sourceKitLspPath = findSourceKitLspPath()
                           ?: throw ExecutionException(LspClientPlaygroundBundle.message("swift.lsp.executable.not.found"))
    return GeneralCommandLine(sourceKitLspPath)
  }
}


class SwiftLspFileType : FileType {
  override fun getName(): String = "Swift"
  override fun getDescription(): String = LspClientPlaygroundBundle.message("swift.filetype.description")
  override fun getDefaultExtension(): String = "swift"
  override fun getIcon(): Icon = AllIcons.General.Language
  override fun isBinary(): Boolean = false
}

private fun isSwiftLspFile(file: VirtualFile): Boolean = file.extension == "swift"

private fun findSourceKitLspPath(): String? {
  val explicitPath = System.getProperty("lsp.client.playground.swift.sourcekit.lsp.path")
  if (!explicitPath.isNullOrBlank()) {
    return explicitPath
  }

  return PathEnvironmentVariableUtil.findFirst("sourcekit-lsp")?.toString()
}
