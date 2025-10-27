package com.intellij.terminal.frontend.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager.Companion.getInstance
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * Service for accessing and creating Reworked Terminal tabs in the Terminal Tool Window.
 * Use [getInstance] to get the instance of the service.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalToolWindowTabsManager {
  /**
   * List of the opened Reworked Terminal tabs in the Terminal Tool Window.
   * Order can be different from the UI.
   */
  @get:RequiresEdt
  val tabs: List<TerminalToolWindowTab>

  /**
   * The entry point for creating a new terminal tab in the Terminal Tool Window.
   * Use [TerminalToolWindowTabBuilder.createTab] to actually create a new tab.
   */
  fun createTabBuilder(): TerminalToolWindowTabBuilder

  /**
   * Close the given tool window tab and terminate the underlying shell process.
   */
  @RequiresEdt
  fun closeTab(tab: TerminalToolWindowTab)

  /**
   * Close the given tool window tab but leave the underlying shell process running.
   * So, the returned [TerminalView] can be able to be used in the other context (for example, to be opened as the editor tab).
   */
  @RequiresEdt
  fun detachTab(tab: TerminalToolWindowTab): TerminalView

  /**
   * Create a new tool window tab with the given [TerminalView].
   * Note, that a shell process of the [TerminalView] should be already started
   * because this method won't start it.
   *
   * @param contentManager the tool window content manager to add the tab to.
   * Worth specifying when the terminal tool window is split to open the tab in the specific area.
   * If it is `null`, the tab will be opened in the top-left split area (or in the main area if there are no splits).
   */
  @RequiresEdt
  fun attachTab(view: TerminalView, contentManager: ContentManager?): TerminalToolWindowTab

  fun addListener(parentDisposable: Disposable, listener: TerminalTabsManagerListener)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): TerminalToolWindowTabsManager = project.service()
  }
}

/**
 * @param content the object that identifies the tool window tab.
 * @return the terminal tool window tab if provided [content] corresponds to Reworked Terminal tab in the Terminal Tool Window.
 */
@ApiStatus.Experimental
@RequiresEdt
fun TerminalToolWindowTabsManager.findTabByContent(content: Content): TerminalToolWindowTab? {
  return tabs.find { it.content == content }
}

@ApiStatus.Experimental
interface TerminalTabsManagerListener {
  /**
   * Called when the terminal tab is added to the terminal tool window.
   * Note that this method is fired both when a new terminal tab is created ([TerminalToolWindowTabBuilder.createTab])
   * and when the terminal tab is attached to the tool window ([TerminalToolWindowTabsManager.attachTab]).
   * So, if you need to perform some actions with [TerminalView] only once, check that they are not performed again
   * (for example, when the tab is attached back to the tool window).
   */
  fun tabAdded(tab: TerminalToolWindowTab)
}