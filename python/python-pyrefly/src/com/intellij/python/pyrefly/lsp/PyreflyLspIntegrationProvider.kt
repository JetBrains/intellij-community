package com.intellij.python.pyrefly.lsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.lsWidget.LspClientWidgetItem
import com.intellij.python.lsp.core.PyLspTool
import com.intellij.python.lsp.core.PyLspToolDescriptor
import com.intellij.python.lsp.core.PyLspToolIntegrationProvider
import com.intellij.python.lsp.core.pyLspAttachedDescriptor
import com.intellij.python.lsp.core.pyLspProjectsToServeWith
import com.intellij.python.pyrefly.PyreflyPyTool
import com.jetbrains.python.project.PyProject

class PyreflyLspIntegrationProvider : PyLspToolIntegrationProvider() {
  override fun getDescriptor(pyProject: PyProject): PyLspToolDescriptor = pyreflyDescriptor(pyProject)

  override fun pyTool(project: Project): PyLspTool<*> = PyreflyPyTool.getInstance()

  override val servesEveryModule: Boolean get() = true

  override fun createWidgetItem(lspClient: LspClient, currentFile: VirtualFile?): LspClientWidgetItem? {
    if ((lspClient.descriptor as? PyreflyLspClientDescriptor)?.runsBundledPyrefly == true) return null
    return super.createWidgetItem(lspClient, currentFile)
  }
}

/**
 * The descriptor to start pyrefly with for [pyProject].
 *
 * Pyrefly keeps one workspace for each folder, with its own interpreter, so one server answers for
 * every Python project of a multi-module project. This holds for the type engine and for the External
 * Tools tool: both start the server from this descriptor. A Python project whose environment pins
 * another pyrefly version gets a server of its own, because one server runs one binary. For a project
 * of one served Python project the server answers for [pyProject] alone.
 */
internal fun pyreflyDescriptor(pyProject: PyProject): PyreflyLspClientDescriptor {
  val servedProjects = pyLspProjectsToServeWith(pyProject, PyreflyPyTool.getInstance())
  val descriptor = PyreflyLspClientDescriptor(servedProjects.first(), servedProjects)
  // The type engine builds a descriptor and starts the server itself, so it must wire the provider
  // here. `fileOpened` may never run for the file that started the server.
  pyLspAttachedDescriptor(descriptor, PyreflyLspIntegrationProvider::class.java)
  return descriptor
}
