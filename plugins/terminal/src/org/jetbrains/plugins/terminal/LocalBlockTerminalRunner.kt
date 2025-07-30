// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.ExperimentalUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.TerminalWidgetImpl

/**
 * Terminal runner that runs the New Terminal (Gen1) with corresponding shell integration.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
open class LocalBlockTerminalRunner(project: Project) : LocalTerminalDirectRunner(project) {
  override fun isGenOneTerminalEnabled(): Boolean {
    return true
  }

  override fun createShellTerminalWidget(parent: Disposable, startupOptions: ShellStartupOptions): TerminalWidget {
    return TerminalWidgetImpl(myProject, settingsProvider, parent)
  }

  @Deprecated("Unused")
  open fun shouldShowPromotion(): Boolean {
    return ExperimentalUI.isNewUI()
           && Registry.`is`(BLOCK_TERMINAL_SHOW_PROMOTION, false)
           && TerminalOptionsProvider.instance.terminalEngine == TerminalEngine.CLASSIC
  }

  companion object {
    const val BLOCK_TERMINAL_REGISTRY: String = "terminal.new.ui"
    const val REWORKED_BLOCK_TERMINAL_REGISTRY: String = "terminal.new.ui.reworked"
    const val BLOCK_TERMINAL_FISH_REGISTRY: String = "terminal.new.ui.fish"
    const val BLOCK_TERMINAL_AUTOCOMPLETION: String = "terminal.new.ui.autocompletion"
    private const val BLOCK_TERMINAL_SHOW_PROMOTION: String = "terminal.new.ui.show.promotion"
    const val REWORKED_TERMINAL_COMPLETION_POPUP: String = "terminal.new.ui.completion.popup"
  }
}
