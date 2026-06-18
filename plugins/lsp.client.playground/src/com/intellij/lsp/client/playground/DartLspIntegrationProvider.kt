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


class DartLspIntegrationProvider : LspIntegrationProvider {
  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
    if (isDartLspFile(file)) {
      clientStarter.ensureClientStarted(DartLspServerDescriptor(project))
    }
  }

  override fun createWidgetItem(lspClient: LspClient, currentFile: VirtualFile?): LspClientWidgetItem {
    return LspClientWidgetItem(lspClient, currentFile, AllIcons.General.Language)
  }
}


class DartLspServerDescriptor(project: Project) : ProjectWideLspClientDescriptor(project, "Dart") {
  override fun isSupportedFile(file: VirtualFile): Boolean = isDartLspFile(file)

  override fun createCommandLine(): GeneralCommandLine {
    val dartPath = findDartPath()
                   ?: throw ExecutionException(LspClientPlaygroundBundle.message("dart.lsp.executable.not.found"))
    return GeneralCommandLine(dartPath, "language-server")
  }
}


class DartLspFileType : FileType {
  override fun getName(): String = "Dart"
  override fun getDescription(): String = LspClientPlaygroundBundle.message("dart.filetype.description")
  override fun getDefaultExtension(): String = "dart"
  override fun getIcon(): Icon = AllIcons.General.Language
  override fun isBinary(): Boolean = false
}

private fun isDartLspFile(file: VirtualFile): Boolean =
  file.extension == "dart"

private fun findDartPath(): String? {
  val explicitPath = System.getProperty("lsp.client.playground.dart.path")
  if (!explicitPath.isNullOrBlank()) {
    return explicitPath
  }

  return PathEnvironmentVariableUtil.findFirst("dart")?.toString()
}
