// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.plugins.terminal.settings.impl.TerminalSessionPersistedTab

private val PENDING_TAB_KEY: Key<TerminalSessionPersistedTab> = Key.create("TerminalPendingTab")

/**
 * Returns the stored tab if this content is a pending tab.
 *
 * A pending tab is a content of the Terminal tool window that has a stored tab and no terminal view yet.
 * The tabs restore adds each stored tab, except the first one, as a pending tab.
 * The pending tab is built into a usual terminal tab when the user selects it for the first time.
 * Until then, [com.intellij.terminal.frontend.toolwindow.getTerminalTab] returns `null` for it.
 */
internal fun Content.getPendingTerminalTab(): TerminalSessionPersistedTab? = getUserData(PENDING_TAB_KEY)

internal fun Content.setPendingTerminalTab(tab: TerminalSessionPersistedTab?) {
  putUserData(PENDING_TAB_KEY, tab)
}

/**
 * Builds this content into a usual terminal tab if it is a pending tab. Does nothing otherwise.
 */
@RequiresEdt(generateAssertion = false /* IJPL-115548 */)
internal fun Content.buildPendingTerminalTab(project: Project) {
  val storedTab = getPendingTerminalTab() ?: return
  // Remove the stored tab first, so a selection change during the build does not build the tab again.
  setPendingTerminalTab(null)

  TerminalToolWindowTabsManager.getInstance(project).createTabBuilder()
    .applyPersistedTab(storedTab)
    .pendingContent(this)
    .createTab()
}

/**
 * Builds a pending tab when it becomes selected in [contentManager] or in its nested content managers.
 * The build is synchronous, so the content manager requests the focus for the built tab.
 */
internal fun installPendingTabsBuilding(
  project: Project,
  contentManager: ContentManager,
  coroutineScope: CoroutineScope,
) {
  val listener = object : ContentManagerListener {
    override fun selectionChanged(event: ContentManagerEvent) {
      if (event.operation == ContentManagerEvent.ContentOperation.add) {
        event.content.buildPendingTerminalTab(project)
      }
    }
  }

  contentManager.addRecursiveContentManagerListener(listener)
  coroutineScope.coroutineContext.job.invokeOnCompletion {
    contentManager.removeRecursiveContentManagerListener(listener)
  }
}
