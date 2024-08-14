// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session

import com.intellij.openapi.Disposable
import com.intellij.terminal.completion.spec.ShellCommandResult
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus

internal interface ShellCommandExecutionManager  {

  /**
   * Runs user command in Shell.
   *
   * It gives no guarantee to start the command immediately.
   * The command could start later (e.g. when the Shell becomes free).
   */
  fun sendCommandToExecute(shellCommand: String)

  /**
   * Generator is an invisible to end-user command used to gain information from the Shell (e.g. history, list of files).
   *
   * The [shellCommand] MUST NOT modify the Shell state (e.g., define/export variable, alias)
   */
  fun runGeneratorAsync(shellCommand: String): Deferred<ShellCommandResult>

  /**
   * Adds the KeyBinding to the queue to be executed when the terminal becomes free.
   * Guaranteed to execute only if the terminal is free.
   * If the terminal is executing user command, then queued Key Bindings could be lost.
   * If a new command starts after the Key Binding execution is queued, then queued Key Binding could be lost and not applied.
   */
  fun sendKeyBinding(keyBinding: KeyBinding)

  fun addListener(listener: ShellCommandSentListener)
  fun addListener(listener: ShellCommandSentListener, parentDisposable: Disposable)
}
