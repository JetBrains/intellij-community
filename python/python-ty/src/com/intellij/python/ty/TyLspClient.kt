package com.intellij.python.ty

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.Lsp4jServer
import com.intellij.platform.lsp.api.customization.LspGoToDefinitionDisabled
import com.intellij.python.lsp.core.PyLspToolCustomization
import com.intellij.python.lsp.core.PyLspToolDescriptor
import com.intellij.python.lsp.core.PyLspToolIntegrationProvider
import com.intellij.python.pytools.lsp.PyLspToolSettings

class TyLspIntegrationProvider : PyLspToolIntegrationProvider() {
  override fun getDescriptor(module: Module): PyLspToolDescriptor =
    TyLspClientDescriptor(module)
}

class TyLspClientDescriptor(module: Module) : PyLspToolDescriptor(module, TyPyTool.getInstance()) {
  override fun lspArguments(): List<String> =
    listOf("server")

  override val toolConfig: PyLspToolSettings
    get() = project.service<TyConfiguration>()

  override val lsp4jServerClass: Class<out Lsp4jServer>
    get() = TyLsp4jServer::class.java

  override val commandDescriptions: Map<String, String?> = mapOf(
    "ty.printDebugInformation" to "Print debug information",
  )

  override val lspCustomization: PyLspToolCustomization = object : PyLspToolCustomization(toolConfig, pyTool, project) {
    // workaround for IJPL-228417
    override val goToDefinitionCustomizer = LspGoToDefinitionDisabled

    override val diagnosticsSupport: PyLspToolDiagnosticsSupport = object : PyLspToolDiagnosticsSupport() {
      override fun shouldAskServerForDiagnostics(file: VirtualFile): Boolean =
        super.shouldAskServerForDiagnostics(file) && !file.isJupyterNotebook()
    }
  }
}

private fun VirtualFile.isJupyterNotebook(): Boolean =
  extension.equals("ipynb", ignoreCase = true)
