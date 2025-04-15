// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.client.sessions
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.ExperimentalUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.TerminalWidgetImpl

/**
 * Terminal runner that runs the terminal with the new block UI if [isGenOneTerminalEnabled] or [isGenTwoTerminalEnabled] is true.
 * In other cases, the classic plain terminal UI is used.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
open class LocalBlockTerminalRunner(project: Project) : LocalTerminalDirectRunner(project) {
  override fun isGenOneTerminalEnabled(): Boolean {
    // The block terminal should be enabled in the New UI and when [BLOCK_TERMINAL_REGISTRY] is enabled.
    // But the New UI is disabled in tests by default, and the result of this method is always false.
    // So, we need to omit the requirement of the New UI for tests.
    return (ExperimentalUI.isNewUI() || ApplicationManager.getApplication().isUnitTestMode)
           && TerminalOptionsProvider.instance.terminalEngine == TerminalEngine.NEW_TERMINAL
  }

  override fun isGenTwoTerminalEnabled(): Boolean {
    return (ExperimentalUI.isNewUI() || ApplicationManager.getApplication().isUnitTestMode)
           && TerminalOptionsProvider.instance.terminalEngine == TerminalEngine.REWORKED
           // Do not enable Gen2 terminal in CodeWithMe until it is adapted to this mode.
           && myProject.sessions(ClientKind.GUEST).isEmpty()
  }

  override fun createShellTerminalWidget(parent: Disposable, startupOptions: ShellStartupOptions): TerminalWidget {
    if (isGenOneTerminalEnabled) {
      return TerminalWidgetImpl(myProject, settingsProvider, parent)
    }
    return super.createShellTerminalWidget(parent, startupOptions)
  }

  open fun shouldShowPromotion(): Boolean {
    return ExperimentalUI.isNewUI()
           && Registry.`is`(BLOCK_TERMINAL_SHOW_PROMOTION, false)
           && TerminalOptionsProvider.instance.terminalEngine == TerminalEngine.CLASSIC
  }

  companion object {
    const val BLOCK_TERMINAL_REGISTRY: String = "terminal.new.ui"
    const val REWORKED_BLOCK_TERMINAL_REGISTRY: String = "terminal.new.ui.reworked"
    const val BLOCK_TERMINAL_FISH_REGISTRY: String = "terminal.new.ui.fish"
    const val BLOCK_TERMINAL_POWERSHELL_WIN11_REGISTRY: String = "terminal.new.ui.powershell.win11"
    const val BLOCK_TERMINAL_POWERSHELL_WIN10_REGISTRY: String = "terminal.new.ui.powershell.win10"
    const val BLOCK_TERMINAL_POWERSHELL_UNIX_REGISTRY: String = "terminal.new.ui.powershell.unix"
    const val BLOCK_TERMINAL_AUTOCOMPLETION: String = "terminal.new.ui.autocompletion"
    private const val BLOCK_TERMINAL_SHOW_PROMOTION: String = "terminal.new.ui.show.promotion"
    const val REWORKED_TERMINAL_COMPLETION_POPUP: String = "terminal.new.ui.completion.popup"
  }
}
