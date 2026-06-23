// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyright

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.python.pytools.lsp.LSP_TOOLS_STORAGE_FILE
import com.intellij.python.pytools.lsp.PyLspToolConfiguration
import com.intellij.python.pytools.ui.PyLspToolDetailConfigurable
import com.intellij.util.xmlb.XmlSerializerUtil

class BasedpyrightConfigurable(
  project: Project,
) : PyLspToolDetailConfigurable<BasedpyrightConfiguration>(
  project = project,
  tool = BasedpyrightPyTool.getInstance()
)

@Service(Service.Level.PROJECT)
@State(
  name = "BasedpyrightConfiguration",
  storages = [Storage(LSP_TOOLS_STORAGE_FILE)]
)
data class BasedpyrightConfiguration(
  override var inlayHints: Boolean? = true,
  override var completions: Boolean? = true,
) : PyLspToolConfiguration<BasedpyrightConfiguration>() {
  override fun loadState(state: BasedpyrightConfiguration) {
    XmlSerializerUtil.copyBean(state, this)
  }
}
