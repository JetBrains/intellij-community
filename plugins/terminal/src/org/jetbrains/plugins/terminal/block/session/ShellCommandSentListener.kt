// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session

import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * @see ShellCommandExecutionManagerImpl
 */
@ApiStatus.Internal
interface ShellCommandSentListener : EventListener {
  /**
   * Called when a user command has been submitted for later execution.
   * Might be called in the background or in the UI thread.
   *
   * Please note this call may happen prior to actual writing bytes to TTY.
   */
  fun userCommandSubmitted(userCommand: String) {}

  /**
   * Called when a user command has been sent for execution in shell.
   * Might be called in the background or in the UI thread.
   *
   * Please note this call may happen prior to actual writing bytes to TTY.
   */
  fun userCommandSent(userCommand: String) {}

  /**
   * Called when a generator command has been sent for execution in shell.
   * Might be called in the background or in the UI thread.
   *
   * Please note this call may happen prior to actual writing bytes to TTY.
   */
  fun generatorCommandSent(generatorCommand: String) {}
}
