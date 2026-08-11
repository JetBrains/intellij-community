package com.intellij.python.pyright

import com.google.gson.JsonObject
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.stopAndRestartClientsIfNeeded
import com.intellij.platform.lsp.api.stopClients
import com.intellij.python.lsp.core.PyLspToolDescriptor
import com.intellij.python.lsp.core.PyLspToolIntegrationProvider
import com.intellij.python.pytools.isEnabledOn
import com.intellij.python.pytools.lsp.PyLspTool
import com.intellij.python.pytools.lsp.PyLspToolSettings
import com.jetbrains.python.PythonPluginDisposable
import org.eclipse.lsp4j.DidChangeConfigurationParams

/** The pyright-family tools sharing one LSP server, in priority order (basedpyright wins over pyright). */
private fun pyrightFamilyTools(): List<PyLspTool<*>> = listOf(
  BasedpyrightPyTool.getInstance(),
  PyrightPyTool.getInstance(),
)

/** The pyright-family tool that should serve a project: the first enabled one, or basedpyright as a (disabled) fallback. */
private fun activePyrightTool(project: Project): PyLspTool<*> {
  val tools = pyrightFamilyTools()
  return tools.firstOrNull { it.isEnabledOn(project) } ?: tools.first()
}

/**
 * Re-evaluate which pyright-family server should serve [project]: restart the shared
 * [PyrightLspIntegrationProvider] clients while either tool is enabled (so the rebuilt descriptor binds to
 * the now-active one), otherwise stop them. Called from each pyright-family tool's `onEnabledChanged`.
 */
internal fun restartOrStopPyrightProvider(project: Project) {
  val manager = LspClientManager.getInstance(project)
  if (pyrightFamilyTools().any { it.isEnabledOn(project) }) manager.stopAndRestartClientsIfNeeded<PyrightLspIntegrationProvider>()
  else manager.stopClients<PyrightLspIntegrationProvider>()
}

class PyrightLspIntegrationProvider : PyLspToolIntegrationProvider() {
  override fun getDescriptor(module: Module): PyLspToolDescriptor =
    PyrightLspClientDescriptor(module)
}

class PyrightLspClientDescriptor(module: Module) : PyLspToolDescriptor(
  module = module,
  pyTool = activePyrightTool(module.project),
) {

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

  override val executableName: String
    get() = "${pyTool.packageName.name}-langserver"

  override fun lspArguments(): List<String> {
    return listOf("server", "--stdio")
  }

  override val toolConfig: PyLspToolSettings
    get() = pyTool.configuration(project)

  override val commandDescriptions: Map<String, String?> = mapOf(
    "pyright.createtypestub" to null,
    "pyright.restartserver" to "Restart server",
    "basedpyright.createtypestub" to null,
    "basedpyright.restartserver" to "Restart server",
    "basedpyright.writeBaseline" to "Write new errors to baseline",
  )
}
