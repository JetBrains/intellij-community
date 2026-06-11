package com.intellij.python.pyright

import com.google.gson.JsonObject
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.python.lsp.core.PyLspToolIntegrationProvider
import com.intellij.python.lsp.core.PyLspToolDescriptor
import com.intellij.python.pytools.lsp.PyLspToolSettings
import com.jetbrains.python.PythonPluginDisposable
import org.eclipse.lsp4j.DidChangeConfigurationParams
import javax.swing.Icon

class PyrightLspIntegrationProvider : PyLspToolIntegrationProvider() {
  override fun getDescriptor(module: Module): PyLspToolDescriptor =
    PyrightLspClientDescriptor(module)

  override fun getIcon(lspClient: LspClient): Icon {
    return when (lspClient.initializeResult?.serverInfo?.name) {
      "basedpyright" -> PyrightUtil.getDefaultBasedPyrightIcon()
      else -> PyrightUtil.getDefaultPyrightIcon()
    }
  }

}

class PyrightLspClientDescriptor(module: Module) : PyLspToolDescriptor(module, PyrightPyTool.getInstance()) {

  init {
    // Send empty settings to make pyright LSP work properly
    LspClientManager.getInstance(project).addListener(object : LspClientManagerListener {
      override fun serverStateChanged(lspClient: LspClient) {
        if (lspClient.descriptor !is PyrightLspClientDescriptor) return

        if (lspClient.state == LspServerState.Running) {
          lspClient.sendNotification { server ->
            server.workspaceService.didChangeConfiguration(DidChangeConfigurationParams(JsonObject()))
          }
        }
      }
    }, PythonPluginDisposable.getInstance(project))
  }

  override fun createInitializationOptions(): Any {
    return JsonObject()
  }

  override val executableNames: List<String>
    get() = listOf("basedpyright-langserver", "pyright-langserver")

  override val isUvxSupported: Boolean
    get() = false

  override fun lspArguments(): List<String> {
    return listOf("server", "--stdio")
  }

  override val toolConfig: PyLspToolSettings
    get() = project.service<PyrightConfiguration>()

  override val commandDescriptions: Map<String, String?> = mapOf(
    "pyright.createtypestub" to null,
    "pyright.restartserver" to "Restart server",
    "basedpyright.createtypestub" to null,
    "basedpyright.restartserver" to "Restart server",
    "basedpyright.writeBaseline" to "Write new errors to baseline",
  )
}
