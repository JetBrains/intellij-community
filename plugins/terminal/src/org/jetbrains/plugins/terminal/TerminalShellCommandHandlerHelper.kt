// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.components.serviceOrNull
import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyEvent

/**
 * Implements custom processing of commands execution in the terminal editor
 */
@ApiStatus.Internal
interface TerminalShellCommandHandlerHelper {
  fun processEnterKeyPressed(e: KeyEvent): Boolean
  fun processKeyPressed(e: KeyEvent)

  /**
   * A service providing access to [TerminalShellCommandHandlerHelper]'s implementation. Since the implementation is located in a separate
   * module, it's not always available.
   */
  interface Factory {
    companion object {
      @JvmStatic
      fun createHelper(widget: ShellTerminalWidget): TerminalShellCommandHandlerHelper {
        return getFactoryInstance()?.createInstance(widget) ?: DummyTerminalShellCommandHandlerHelper
      }

      @JvmStatic
      fun isFeatureEnabled(): Boolean {
        return getFactoryInstance()?.isEnabled == true
      }

      @JvmStatic
      fun canProcessCustomKey(keyEvent: KeyEvent): Boolean {
        return getFactoryInstance()?.canProcessCustomKeyEvent(keyEvent) == true
      }

      private fun getFactoryInstance(): Factory? = serviceOrNull<Factory>()
    }

    val isEnabled: Boolean
    fun createInstance(widget: ShellTerminalWidget): TerminalShellCommandHandlerHelper
    fun canProcessCustomKeyEvent(keyEvent: KeyEvent): Boolean
  }
}

private object DummyTerminalShellCommandHandlerHelper : TerminalShellCommandHandlerHelper {
  override fun processEnterKeyPressed(e: KeyEvent): Boolean = false
  override fun processKeyPressed(e: KeyEvent) {}
}