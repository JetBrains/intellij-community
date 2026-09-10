package com.intellij.python.pyrefly.lsp

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.python.lsp.core.PyLspTool
import com.intellij.python.lsp.core.PyLspToolDescriptor
import com.intellij.python.lsp.core.PyLspToolIntegrationProvider
import com.intellij.python.lsp.core.pyLspAttachedDescriptor
import com.intellij.python.lsp.core.pyLspModulesToServeWith
import com.intellij.python.pyrefly.PyreflyPyTool

class PyreflyLspIntegrationProvider : PyLspToolIntegrationProvider() {
  override fun getDescriptor(module: Module): PyLspToolDescriptor = pyreflyDescriptor(module)

  override fun pyTool(project: Project): PyLspTool<*> = PyreflyPyTool.getInstance()

  override val servesEveryModule: Boolean get() = true
}

/**
 * The descriptor to start pyrefly with for [module].
 *
 * Pyrefly keeps one workspace for each folder, with its own interpreter, so one server answers for
 * every module of a multi-module project. This holds for the type engine and for the External Tools
 * tool: both start the server from this descriptor. A module whose environment pins another pyrefly
 * version gets a server of its own, because one server runs one binary. For a project of one served
 * module the server answers for [module] alone.
 */
internal fun pyreflyDescriptor(module: Module): PyreflyLspClientDescriptor {
  val servedModules = pyLspModulesToServeWith(module, PyreflyPyTool.getInstance())
  val descriptor = PyreflyLspClientDescriptor(servedModules.first(), servedModules)
  // The type engine builds a descriptor and starts the server itself, so it must wire the provider
  // here. `fileOpened` may never run for the file that started the server.
  pyLspAttachedDescriptor(descriptor, PyreflyLspIntegrationProvider::class.java)
  return descriptor
}
