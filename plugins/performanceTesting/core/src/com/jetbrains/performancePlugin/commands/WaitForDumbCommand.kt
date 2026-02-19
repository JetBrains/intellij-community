// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier

class WaitForDumbCommand (text: String, line: Int) : AbstractCommand(text, line) {
  companion object {

    const val PREFIX = CMD_PREFIX + "waitForDumb"
  }
  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback = ActionCallbackProfilerStopper()
    val maxWaitingTime = extractCommandArgument(PREFIX)
    Waiter.checkCondition(BooleanSupplier { DumbService.getInstance(context.project).isDumb }).await(maxWaitingTime.toLong(), TimeUnit.SECONDS)
    actionCallback.setDone()
    return actionCallback.toPromise()
  }
}