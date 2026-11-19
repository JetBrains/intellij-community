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

/** Runs the External Tools Pyrefly, the `pyrefly` of the environment. */
class PyreflyLspIntegrationProvider : PyLspToolIntegrationProvider() {
  override fun getDescriptor(pyProject: PyProject): PyLspToolDescriptor = pyreflyDescriptor(pyProject)

  override fun pyTool(project: Project): PyLspTool<*> = PyreflyPyTool.getInstance()

  override val servesEveryModule: Boolean get() = true
}

/**
 * Runs the bundled Pyrefly, the binary of the plugin.
 *
 * Only the type engine selection starts and stops it, see [PyreflyPyTool.runsBundledPyrefly]. The `pyrefly`
 * package of an environment does not change its binary, so a change of that package does not restart it.
 */
class BundledPyreflyLspIntegrationProvider : PyLspToolIntegrationProvider() {
  override fun getDescriptor(pyProject: PyProject): PyLspToolDescriptor = bundledPyreflyDescriptor(pyProject)

  override fun pyTool(project: Project): PyLspTool<*> = PyreflyPyTool.getInstance()

  override fun isActiveOn(project: Project): Boolean = PyreflyPyTool.getInstance().runsBundledPyrefly(project)

  override val followsToolPackage: Boolean get() = false

  override val servesEveryModule: Boolean get() = true

  override fun createWidgetItem(lspClient: LspClient, currentFile: VirtualFile?): LspClientWidgetItem? = null
}

/**
 * The descriptor to start the External Tools Pyrefly with for [pyProject].
 *
 * Pyrefly keeps one workspace for each folder, with its own interpreter, so one server answers for
 * every Python project of a multi-module project. A Python project whose environment pins another
 * pyrefly version gets a server of its own, because one server runs one binary. For a project of one
 * served Python project the server answers for [pyProject] alone.
 */
internal fun pyreflyDescriptor(pyProject: PyProject): PyreflyLspClientDescriptor {
  val servedProjects = pyLspProjectsToServeWith(pyProject, PyreflyPyTool.getInstance())
  val descriptor = PyreflyLspClientDescriptor(servedProjects.first(), servedProjects)
  // The type engine builds a descriptor and starts the server itself, so it must wire the provider
  // here. `fileOpened` may never run for the file that started the server.
  pyLspAttachedDescriptor(descriptor, PyreflyLspIntegrationProvider::class.java)
  return descriptor
}

/**
 * The descriptor to start the bundled Pyrefly with for [pyProject].
 *
 * One binary serves every Python project, so the version of `pyrefly` in an environment does not split them.
 */
internal fun bundledPyreflyDescriptor(pyProject: PyProject): PyreflyLspClientDescriptor {
  val servedProjects = pyLspProjectsToServeWith(pyProject, PyreflyPyTool.getInstance(), followsToolPackage = false)
  val descriptor = PyreflyLspClientDescriptor(servedProjects.first(), servedProjects, runsBundledPyrefly = true)
  pyLspAttachedDescriptor(descriptor, BundledPyreflyLspIntegrationProvider::class.java)
  return descriptor
}

/** The descriptor of the server that the type engine reads its types from, see [PyreflyPyTool.typeEngineProviderClass]. */
internal fun pyreflyTypeEngineDescriptor(pyProject: PyProject): PyreflyLspClientDescriptor =
  if (PyreflyPyTool.isBundledPyreflyEnabled()) bundledPyreflyDescriptor(pyProject) else pyreflyDescriptor(pyProject)
