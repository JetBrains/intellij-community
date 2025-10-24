package com.intellij.terminal.frontend.view

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Key events handling in the terminal is different from the other places of the IDE.
 * IntelliJ Terminal has an option "Override IDE shortcuts" that limits the list of actions that can be executed
 * by shortcuts in the terminal to avoid conflicts of the IDE actions with the shell key bindings.
 * For example, to make Ctrl+R shortcut to be handled by the shell (and invoke search in commands history)
 * instead of starting a Run Configuration in the IDE.
 *
 * This extension point allows extending the list of actions that are allowed to be executed in the terminal by shortcut.
 * Override this interface and register it as an extension in your plugin
 * if you have actions that should be executed in the terminal by shortcut.
 *
 * See https://plugins.jetbrains.com/docs/intellij/action-system.html for more details about declaring actions.
 *
 * Note that this extension point is working only with the Reworked Terminal.
 */
@ApiStatus.Experimental
interface TerminalAllowedActionsProvider {
  /**
   * Returns the list of Action IDs that should be allowed to be executed in the terminal by shortcut.
   */
  fun getActionIds(): List<String>

  companion object {
    internal val EP_NAME = ExtensionPointName.create<TerminalAllowedActionsProvider>("org.jetbrains.plugins.terminal.allowedActionsProvider")
  }
}