// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.frontend.ui.packagemanagers

import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.intellij.python.pytools.frontend.PyToolFrontend as PyTool
import com.intellij.python.pytools.frontend.PackageManagerPyToolFrontend as PackageManagerPyTool
import com.intellij.python.pytools.frontend.ui.PyToolsUiBundle

/**
 * Indexes each package manager's presentable name for the IDE's global Settings search; a hit
 * navigates to the Package Managers page, where [PyPackageManagersConfigurable.enableSearch] selects
 * and scrolls the matching row.
 */
internal class PyPackageManagersSearchableOptionContributor : SearchableOptionContributor() {
  override fun processOptions(processor: SearchableOptionProcessor) {
    val displayName = PyToolsUiBundle.message("settings.package.managers.title")
    for (tool in PyTool.extensionList.filterIsInstance<PackageManagerPyTool>()) {
      processor.addOptions(
        tool.presentableName,
        null,
        tool.presentableName,
        PyPackageManagersConfigurable.ID,
        displayName,
        false,
      )
    }
  }
}
