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

  /** When formatting is enabled, also sort imports (rule `I`) as part of `Reformat Code`, without removing unused ones. */
  var formatSortImports: Boolean

  /** Run `ruff check --fix-only` (apply all configured safe lint fixes) whenever a Python file is saved. */
  var fixOnSave: Boolean

  /**
   * Run `ruff check --fix-only` over the committed Python files before a commit completes. Toggled from the
   * "Apply Ruff fixes" checkbox in the commit options, not from the External Tools settings page.
   */
  var fixOnCommit: Boolean
}

@Service(Service.Level.PROJECT)
@State(
  name = "RuffConfiguration",
  storages = [Storage(LSP_TOOLS_STORAGE_FILE)]
)
data class RuffConfiguration(
  override var sortImports: Boolean = true,
  override var formatting: Boolean = true,
  override var formatSortImports: Boolean = false,
  override var fixOnSave: Boolean = false,
  override var fixOnCommit: Boolean = false,
) : PyLspToolConfiguration<RuffConfiguration>(), RuffSettings {
  override fun loadState(state: RuffConfiguration) {
    XmlSerializerUtil.copyBean(state, this)
  }
}
