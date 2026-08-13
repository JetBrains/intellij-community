// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabPersistenceProvider
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.toolwindow.getTerminalTab
import com.intellij.terminal.frontend.toolwindow.impl.TerminalToolWindowTabsManagerImpl
import com.intellij.terminal.frontend.toolwindow.impl.computePersistedTab
import com.intellij.ui.content.Content
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.settings.impl.TerminalSessionPersistedTab

/**
 * Persists a terminal editor tab as the same [TerminalSessionPersistedTab] record the terminal tool window already uses
 * for its own tabs, so a restored tab is set up exactly the way a restored tool window tab is.
 *
 * Only the reworked terminal is supported: the classic terminal has no equivalent of [computePersistedTab].
 */
@ApiStatus.Internal
class TerminalToolWindowEditorTabPersistenceProvider : ToolWindowEditorTabPersistenceProvider {

  override fun canSerialize(content: Content): Boolean = content.getTerminalTab() != null

  @RequiresEdt
  override fun serialize(content: Content): Element {
    val tab = requireNotNull(content.getTerminalTab()) { "Not a reworked terminal content: $content" }

    return XmlSerializer.serialize(computePersistedTab(tab))
  }

  @RequiresEdt
  override fun deserialize(project: Project, element: Element): Content? {
    if (!TrustedProjects.isProjectTrusted(project)) return null

    val persistedTab = XmlSerializer.deserialize(element, TerminalSessionPersistedTab::class.java)

    return terminalTabsManager(project).createDetachedTab(persistedTab).content
  }

  private fun terminalTabsManager(project: Project): TerminalToolWindowTabsManagerImpl =
    TerminalToolWindowTabsManager.getInstance(project) as TerminalToolWindowTabsManagerImpl
}
