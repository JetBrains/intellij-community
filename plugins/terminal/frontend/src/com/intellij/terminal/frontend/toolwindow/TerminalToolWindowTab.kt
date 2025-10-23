package com.intellij.terminal.frontend.toolwindow

import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.ui.content.Content
import org.jetbrains.annotations.ApiStatus

/**
 * Represents the Reworked Terminal tab in the Terminal Tool Window.
 *
 * The lifetime of this object is bound to the lifetime of the [content].
 * Once the [content] is disposed, the tab is removed from the Terminal Tool Window.
 * But the [view] can have a longer lifetime, because it can be detached from the tab ([TerminalToolWindowTabsManager.detachTab]).
 *
 * Do not dispose the [content] and do not cancel the [view] coroutine scope manually.
 * Use [TerminalToolWindowTabsManager.closeTab] instead.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalToolWindowTab {
  /**
   * The UI and APIs of the terminal in the tool window tab.
   */
  val view: TerminalView

  /**
   * Represents the tool window tab.
   * Added to the [com.intellij.openapi.wm.ToolWindow.getContentManager] of the Terminal Tool Window
   * (or to the nested content managers in case of tool window split).
   */
  val content: Content
}