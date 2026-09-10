package com.intellij.python.ty

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.Lsp4jServer
import com.intellij.platform.lsp.api.customization.LspGoToDefinitionDisabled
import com.intellij.python.lsp.core.PyLspTool
import com.intellij.python.lsp.core.PyLspToolCustomization
import com.intellij.python.lsp.core.PyLspToolIntegrationProvider
import com.intellij.python.lsp.core.PyLspToolDescriptor
import com.intellij.python.lsp.core.PyLspToolSettings
import com.intellij.python.lsp.core.pyLspAttachedDescriptor
import com.intellij.python.lsp.core.pyLspModulesToServeWith

class TyLspIntegrationProvider : PyLspToolIntegrationProvider() {
  override fun getDescriptor(module: Module): PyLspToolDescriptor = tyDescriptor(module)

  override fun pyTool(project: Project): PyLspTool<*> = TyPyTool.getInstance()

  override val servesEveryModule: Boolean get() = true
}

/**
 * The descriptor to start ty with for [module].
 *
 * ty keeps its settings for each workspace folder and resolves the environment against that
 * folder's root, so one server answers for every module of a multi-module project. This holds for
 * the type engine and for the External Tools tool: both start the server from this descriptor. A
 * module whose environment pins another ty version gets a server of its own, because one server runs
 * one binary. For a project of one served module the server answers for [module] alone.
 *
 * We send ty no interpreter. It finds the environment of each folder itself, and its
 * `python_extension` option refuses to be merged with the initialization options.
 */
internal fun tyDescriptor(module: Module): TyLspClientDescriptor {
  val served = pyLspModulesToServeWith(module, TyPyTool.getInstance())
  val descriptor = TyLspClientDescriptor(served.first(), served)
  // The type engine builds a descriptor and starts the server itself, so it must wire the provider
  // here. `fileOpened` may never run for the file that started the server.
  pyLspAttachedDescriptor(descriptor, TyLspIntegrationProvider::class.java)
  return descriptor
}

class TyLspClientDescriptor(
  module: Module,
  servedModules: List<Module> = listOf(module),
) : PyLspToolDescriptor(module, TyPyTool.getInstance(), servedModules) {
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
