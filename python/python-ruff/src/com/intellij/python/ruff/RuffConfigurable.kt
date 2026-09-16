// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.python.lsp.core.LSP_TOOLS_STORAGE_FILE
import com.intellij.python.lsp.core.PyLspToolConfiguration
import com.intellij.python.lsp.core.PyLspToolSettings
import com.intellij.util.xmlb.XmlSerializerUtil

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
