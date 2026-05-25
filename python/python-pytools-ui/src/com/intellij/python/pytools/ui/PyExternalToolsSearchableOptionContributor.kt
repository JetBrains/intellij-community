// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui

import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.ui.configuration.PyExternalToolsConfigurable

/**
 * Indexes each [PyTool]'s presentable name so it shows up as a hit in the IDE's global Settings
 * search (the search field at the top of the Settings dialog). Clicking a hit navigates to the
 * External Tools page; [com.intellij.python.pytools.ui.configuration.PyExternalToolsConfigurable.enableSearch] then selects the matching row.
 */
internal class PyExternalToolsSearchableOptionContributor : SearchableOptionContributor() {
  override fun processOptions(processor: SearchableOptionProcessor) {
    val displayName = PyToolsUiBundle.message("settings.external.tools.title")
    for (tool in PyTool.EP_NAME.extensionList) {
      processor.addOptions(
        tool.presentableName,
        null,
        tool.presentableName,
        PyExternalToolsConfigurable.ID,
        displayName,
        false,
      )
    }
  }
}
