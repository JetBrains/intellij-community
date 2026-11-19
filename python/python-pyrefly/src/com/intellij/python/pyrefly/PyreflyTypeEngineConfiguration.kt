// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyrefly

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.lsp.core.LSP_TOOLS_STORAGE_FILE
import com.intellij.python.lsp.core.PyLspToolConfiguration
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * The editor features of the bundled Pyrefly server.
 *
 * Only the Type Engine page edits it, and only the bundled server reads it. The External Tools Pyrefly reads
 * [PyreflyConfiguration]. The inspections and the completions stay on.
 */
@Service(Service.Level.PROJECT)
@State(
  name = "PyreflyTypeEngineConfiguration",
  storages = [Storage(LSP_TOOLS_STORAGE_FILE)]
)
data class PyreflyTypeEngineConfiguration(
  override var inlayHints: Boolean? = false,
  override var completions: Boolean? = true,
  override var documentation: Boolean? = false,
) : PyLspToolConfiguration<PyreflyTypeEngineConfiguration>() {
  override fun loadState(state: PyreflyTypeEngineConfiguration) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    fun getInstance(project: Project): PyreflyTypeEngineConfiguration = project.service()
  }
}
