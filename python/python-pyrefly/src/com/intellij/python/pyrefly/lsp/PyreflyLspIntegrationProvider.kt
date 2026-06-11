package com.intellij.python.pyrefly.lsp

import com.intellij.openapi.module.Module
import com.intellij.platform.lsp.api.LspClient
import com.intellij.python.lsp.core.PyLspToolIntegrationProvider
import com.intellij.python.lsp.core.PyLspToolDescriptor
import com.intellij.python.pyrefly.PyreflyUtil
import javax.swing.Icon

class PyreflyLspIntegrationProvider : PyLspToolIntegrationProvider() {
  override fun getDescriptor(module: Module): PyLspToolDescriptor =
    PyreflyLspClientDescriptor(module)

  override fun getIcon(lspClient: LspClient): Icon =
    PyreflyUtil.getDefaultPyreflyIcon()
}