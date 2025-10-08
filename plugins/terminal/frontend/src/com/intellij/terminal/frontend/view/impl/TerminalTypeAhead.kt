package com.intellij.terminal.frontend.view.impl

import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * Interface for the terminal type-ahead feature.
 * To support immediate output model updates for some user actions, without waiting for the shell response.
 * Note that the type-ahead might be available only in some contexts.
 */
internal interface TerminalTypeAhead {
  /**
   * Tries to insert the given [string] at the current cursor position.
   * If it is not possible, do nothing.
   */
  @RequiresEdt
  fun type(string: String)

  /**
   * Tries to remove the character before the current cursor position.
   * If it is not possible, do nothing.
   */
  @RequiresEdt
  fun backspace()

  companion object {
    val KEY: Key<TerminalTypeAhead> = Key.create("TerminalTypeAhead")
  }
}