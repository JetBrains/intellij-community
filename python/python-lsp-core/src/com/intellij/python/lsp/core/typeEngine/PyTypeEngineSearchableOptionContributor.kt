// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.typeEngine

import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.intellij.python.lsp.core.PyLspCoreBundle

/**
 * Indexes each type engine's name, so a search for "Pyrefly" or "ty" finds the Type Engine page in
 * the IDE's global Settings search. The page builds the engine buttons at runtime from the
 * [PyTypeEngineProvider] extensions, so the build-time indexer cannot see the names.
 */
internal class PyTypeEngineSearchableOptionContributor : SearchableOptionContributor() {
  override fun processOptions(processor: SearchableOptionProcessor) {
    val displayName = PyLspCoreBundle.message("display.name")
    for (provider in PyTypeEngineProvider.EP.extensionsIfPointIsRegistered) {
      val name = provider.pyTypeEngineType.displayName
      processor.addOptions(
        name,
        null,
        name,
        TYPE_ENGINE_CONFIGURABLE_ID,
        displayName,
        false,
      )
    }
  }

  companion object {
    /** Matches the `projectConfigurable` id in `intellij.python.typeEngine.xml`; the frontend module is not a dependency here. */
    private const val TYPE_ENGINE_CONFIGURABLE_ID = "com.intellij.python.lsp.core.PyTypeEngineConfigurable"
  }
}
