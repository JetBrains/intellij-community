package com.intellij.terminal.frontend.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.TerminalTabBuilder
import com.intellij.terminal.frontend.TerminalView
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalToolWindowTabsManager {
  @get:RequiresEdt
  val tabs: List<TerminalToolWindowTab>

  fun createTabBuilder(): TerminalTabBuilder

  @RequiresEdt
  fun closeTab(tab: TerminalToolWindowTab)

  @RequiresEdt
  fun detachTab(tab: TerminalToolWindowTab): TerminalView

  fun addListener(parentDisposable: Disposable, listener: TerminalTabsManagerListener)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): TerminalToolWindowTabsManager = project.service()
  }
}

@ApiStatus.Experimental
interface TerminalTabsManagerListener : EventListener {
  fun tabCreated(tab: TerminalToolWindowTab)
}