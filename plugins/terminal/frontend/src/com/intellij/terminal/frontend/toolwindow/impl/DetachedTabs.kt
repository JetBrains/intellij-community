package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.session.TerminalTabsManager
import com.intellij.terminal.frontend.toolwindow.TerminalTabsManagerListener
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Notifies [TerminalTabsManager] when a terminal tab is detached,
 * so it isn't restored on the next IDE start.
 */
internal class DetachedTabs(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {

  private val lock = Any()
  private val detachedViews: MutableSet<TerminalView> = Collections.newSetFromMap(IdentityHashMap())
  private val viewsWithIds: MutableMap<TerminalView, Int> = IdentityHashMap()

  init {
    project.messageBus.connect(coroutineScope).subscribe(TerminalTabsManagerListener.TOPIC, object : TerminalTabsManagerListener {
      override fun tabDetached(tab: TerminalToolWindowTab) {
        onTabDetached(tab.view)
      }
    })
  }

  private fun onTabDetached(detachedView: TerminalView) {
    synchronized(lock) {
      detachedViews.add(detachedView)
    }
    tryMatchAndDetach(detachedView)
  }

  /** May be called before or after [onTabDetached]. */
  @OptIn(AwaitCancellationAndInvoke::class)
  fun onTabCreated(terminalView: TerminalView, tabId: Int) {
    synchronized(lock) {
      viewsWithIds[terminalView] = tabId
    }
    tryMatchAndDetach(terminalView)
    terminalView.coroutineScope.awaitCancellationAndInvoke {
      synchronized(lock) {
        viewsWithIds.remove(terminalView)
        detachedViews.remove(terminalView)
      }
    }
  }

  private fun tryMatchAndDetach(view: TerminalView) {
    val tabId = synchronized(lock) {
      if (view in detachedViews && view in viewsWithIds) {
        detachedViews.remove(view)
        viewsWithIds.remove(view)
      }
      else {
        null
      }
    }
    if (tabId != null) {
      coroutineScope.launch {
        TerminalTabsManager.getInstance(project).detachTerminalTab(tabId)
      }
    }
  }
}
