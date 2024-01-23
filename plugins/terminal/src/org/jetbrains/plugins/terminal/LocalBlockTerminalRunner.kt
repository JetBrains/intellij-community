// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.ExperimentalUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.exp.TerminalWidgetImpl

/**
 * Terminal runner that runs the terminal with the new block UI if [isBlockTerminalEnabled] is true.
 * In other cases, the classic plain terminal UI is used.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
open class LocalBlockTerminalRunner(project: Project) : LocalTerminalDirectRunner(project) {
  override fun isBlockTerminalEnabled(): Boolean {
    // The block terminal should be enabled in the New UI and when [BLOCK_TERMINAL_REGISTRY] is enabled.
    // But the New UI is disabled in tests by default, and the result of this method is always false.
    // So, we need to omit the requirement of the New UI for tests.
    return (ExperimentalUI.isNewUI() || ApplicationManager.getApplication().isUnitTestMode)
           && Registry.`is`(BLOCK_TERMINAL_REGISTRY, false)
  }

  override fun createShellTerminalWidget(parent: Disposable, startupOptions: ShellStartupOptions): TerminalWidget {
    if (isBlockTerminalEnabled) {
      return TerminalWidgetImpl(myProject, settingsProvider, parent)
    }
    return super.createShellTerminalWidget(parent, startupOptions)
  }

  companion object {
    const val BLOCK_TERMINAL_REGISTRY: String = "terminal.new.ui"
    const val BLOCK_TERMINAL_FISH_REGISTRY: String = "terminal.new.ui.fish"
    const val BLOCK_TERMINAL_POWERSHELL_WIN11_REGISTRY: String = "terminal.new.ui.powershell.win11"
    const val BLOCK_TERMINAL_POWERSHELL_WIN10_REGISTRY: String = "terminal.new.ui.powershell.win10"
  }
}