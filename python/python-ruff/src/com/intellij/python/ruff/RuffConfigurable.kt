// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.pytools.lsp.LSP_TOOLS_STORAGE_FILE
import com.intellij.python.pytools.lsp.PyLspToolConfiguration
import com.intellij.python.pytools.lsp.PyLspToolSettings
import com.intellij.python.pytools.ui.PyLspToolDetailConfigurable
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Project-wide Ruff detail configurable.
 *
 * Path / discovery mode / enabled state live in [com.intellij.python.pytools.PyToolsState] and are edited from the
 * `External Tools` table; this dialog only exposes the tool-specific feature toggles.
 */
class RuffConfigurable(project: Project) :
  PyLspToolDetailConfigurable(project, RuffPyTool.getInstance()) {

  override val settings: RuffConfiguration get() = project.service<RuffConfiguration>()

  override fun Panel.extraRows() {
    row("") {
      checkBox(RuffBundle.message("checkbox.formatting")).bindSelected(settings::formatting)
    }
    row("") {
      checkBox(RuffBundle.message("checkbox.import.optimizer")).bindSelected(settings::sortImports)
    }
  }
}

interface RuffSettings : PyLspToolSettings {
  var sortImports: Boolean
  var formatting: Boolean
}

@Service(Service.Level.PROJECT)
@State(
  name = "RuffConfiguration",
  storages = [Storage(LSP_TOOLS_STORAGE_FILE)]
)
data class RuffConfiguration(
  override var sortImports: Boolean = true,
  override var formatting: Boolean = true,
) : PyLspToolConfiguration<RuffConfiguration>(), RuffSettings {
  override fun loadState(state: RuffConfiguration) {
    XmlSerializerUtil.copyBean(state, this)
  }
}
