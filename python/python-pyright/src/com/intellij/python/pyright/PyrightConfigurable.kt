// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyright

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.python.lsp.core.LSP_TOOLS_STORAGE_FILE
import com.intellij.python.lsp.core.PyLspToolConfiguration
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(
  name = "PyrightConfiguration",
  storages = [Storage(LSP_TOOLS_STORAGE_FILE)]
)
data class PyrightConfiguration(
  override var completions: Boolean? = true,
) : PyLspToolConfiguration<PyrightConfiguration>() {
  override fun loadState(state: PyrightConfiguration) {
    XmlSerializerUtil.copyBean(state, this)
  }
}
