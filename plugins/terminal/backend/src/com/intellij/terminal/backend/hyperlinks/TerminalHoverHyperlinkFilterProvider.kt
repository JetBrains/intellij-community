// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend.hyperlinks

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.InvisibleHyperlinkFilterProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.terminal.hyperlinks.filter.TerminalFilterScope
import org.jetbrains.plugins.terminal.hyperlinks.filter.createTerminalGenericFileFilter

/**
 * Provides the file path filter for the hovered terminal output line.
 *
 * When `terminal.hyperlinks.on.hover` is off, this provider contributes nothing
 * and `TerminalGenericFileFilterProvider` applies the same filter to every output line.
 */
internal class TerminalHoverHyperlinkFilterProvider : InvisibleHyperlinkFilterProvider {
  override fun getFilters(project: Project, scope: GlobalSearchScope): List<Filter> {
    if (scope !is TerminalFilterScope || !Registry.`is`("terminal.hyperlinks.on.hover", true)) return emptyList()
    return listOfNotNull(createTerminalGenericFileFilter(project, scope))
  }
}
