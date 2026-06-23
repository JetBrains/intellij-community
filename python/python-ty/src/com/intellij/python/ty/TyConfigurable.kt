// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ty

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.python.pytools.lsp.LSP_TOOLS_STORAGE_FILE
import com.intellij.python.pytools.lsp.PyLspToolConfiguration
import com.intellij.python.pytools.ui.PyLspToolDetailConfigurable
import com.intellij.util.xmlb.XmlSerializerUtil

class TyConfigurable(project: Project) :
  PyLspToolDetailConfigurable<TyConfiguration>(project, TyPyTool.getInstance())

@Service(Service.Level.PROJECT)
@State(
  name = "TyConfiguration",
  storages = [Storage(LSP_TOOLS_STORAGE_FILE)]
)
data class TyConfiguration(
  override var inlayHints: Boolean? = true,
  override var completions: Boolean? = true,
  override var documentation: Boolean? = true,
) : PyLspToolConfiguration<TyConfiguration>() {
  override fun loadState(state: TyConfiguration) {
    XmlSerializerUtil.copyBean(state, this)
  }
}
