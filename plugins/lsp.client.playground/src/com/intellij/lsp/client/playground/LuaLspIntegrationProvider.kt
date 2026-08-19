// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lsp.client.playground

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


class LuaLspIntegrationProvider : LspIntegrationProvider {
  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
    if (isLuaLspFile(file)) {
      clientStarter.ensureClientStarted(LuaLspServerDescriptor(project))
    }
  }

  override fun createWidgetItem(lspClient: LspClient, currentFile: VirtualFile?): LspClientWidgetItem {
    return LspClientWidgetItem(lspClient, currentFile, AllIcons.General.Language)
  }
}


class LuaLspServerDescriptor(project: Project) : ProjectWideLspClientDescriptor(project, "Lua") {
  override fun isSupportedFile(file: VirtualFile): Boolean = isLuaLspFile(file)

  override fun createCommandLine(): GeneralCommandLine {
    val luaLanguageServerPath = findLuaLanguageServerPath()
                                ?: throwMissingLspExecutable(project, "Lua", "lua.lsp.executable.not.found")
    return GeneralCommandLine(luaLanguageServerPath)
  }
}


class LuaLspFileType : FileType {
  override fun getName(): String = "Lua"
  override fun getDescription(): String = LspClientPlaygroundBundle.message("lua.filetype.description")
  override fun getDefaultExtension(): String = "lua"
  override fun getIcon(): Icon = AllIcons.General.Language
  override fun isBinary(): Boolean = false
}

private fun isLuaLspFile(file: VirtualFile): Boolean = file.extension == "lua"

private fun findLuaLanguageServerPath(): String? {
  val explicitPath = System.getProperty("lsp.client.playground.lua.language.server.path")
  if (!explicitPath.isNullOrBlank()) {
    return explicitPath
  }

  return PathEnvironmentVariableUtil.findFirst("lua-language-server")?.toString()
}
