// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.openapi.client.ClientProjectSession
import com.intellij.ui.viewModel.extraction.ToolWindowExtractorMode
import com.intellij.ui.viewModel.extraction.ToolWindowViewModelExtractor

/**
 * Switches the Python Packages tool window to `PROJECTOR_INSTANCING` for Remote Dev controller
 * clients. The redesigned PPTW relies on custom cell-renderer paint and hover-driven
 * repaints that BeControl `mirror` cannot replicate on a thin client; projecting the host-side
 * tool window over LUX preserves the local Swing semantics at the cost of extra bandwidth, which
 * is acceptable for a small, low-frequency panel. Other client kinds (CWM guests, local) fall
 * through to the default `mirror` mode declared in `intellij.python.community.impl.xml`.
 */
internal class PyPackagesToolWindowExtractor : ToolWindowViewModelExtractor {
  override fun isApplicable(toolWindowId: String, session: ClientProjectSession): Boolean {
    return toolWindowId == "Python Packages" && session.type.isController
  }

  override fun getMode(): ToolWindowExtractorMode = ToolWindowExtractorMode.PROJECTOR_INSTANCING
}
