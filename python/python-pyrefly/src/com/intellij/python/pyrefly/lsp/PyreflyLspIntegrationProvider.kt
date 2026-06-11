package com.intellij.python.pyrefly.lsp

import com.intellij.openapi.module.Module
import com.intellij.python.lsp.core.PyLspToolIntegrationProvider
import com.intellij.python.lsp.core.PyLspToolDescriptor

class PyreflyLspIntegrationProvider : PyLspToolIntegrationProvider() {
  override fun getDescriptor(module: Module): PyLspToolDescriptor = PyreflyLspClientDescriptor(module)
}