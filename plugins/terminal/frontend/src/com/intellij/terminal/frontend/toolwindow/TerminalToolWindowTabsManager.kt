package com.intellij.terminal.frontend.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalToolWindowTabsManager {
  @get:RequiresEdt
  val tabs: List<TerminalToolWindowTab>

  fun createTabBuilder(): TerminalToolWindowTabBuilder

  @RequiresEdt
  fun closeTab(tab: TerminalToolWindowTab)

  @RequiresEdt
  fun detachTab(tab: TerminalToolWindowTab): TerminalView

  @RequiresEdt
  fun attachTab(view: TerminalView, contentManager: ContentManager?): TerminalToolWindowTab

  fun addListener(parentDisposable: Disposable, listener: TerminalTabsManagerListener)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): TerminalToolWindowTabsManager = project.service()
  }
}

@ApiStatus.Experimental
@RequiresEdt
fun TerminalToolWindowTabsManager.findTabByContent(content: Content): TerminalToolWindowTab? {
  return tabs.find { it.content == content }
}

@ApiStatus.Experimental
interface TerminalTabsManagerListener {
  fun tabCreated(tab: TerminalToolWindowTab)
}