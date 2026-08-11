// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyrefly

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.python.pytools.lsp.LSP_TOOLS_STORAGE_FILE
import com.intellij.python.pytools.lsp.PyLspToolConfiguration
import com.intellij.util.xmlb.XmlSerializerUtil


@Service(Service.Level.PROJECT)
@State(
  name = "PyreflyConfiguration2",
  storages = [Storage(LSP_TOOLS_STORAGE_FILE)]
)
data class PyreflyConfiguration(
  override var inlayHints: Boolean? = false,
  override var completions: Boolean? = false,
  override var documentation: Boolean? = false,
) : PyLspToolConfiguration<PyreflyConfiguration>() {
  override fun loadState(state: PyreflyConfiguration) {
    XmlSerializerUtil.copyBean(state, this)
  }
}
