// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.ActionCallback
import com.jetbrains.JBR
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

internal abstract class AbstractGCCommand(text: String, line: Int) : AbstractCommand(text, line) {
  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback: ActionCallback = ActionCallbackProfilerStopper()
    val ref = WeakReference(IntArray(32 * 1024))
    try {
      Waiter.checkCondition {
        performGC()
        (ref.get() == null).also { done ->
          if (done) {
            actionCallback.setDone()
          }
        }
      }.await(1, TimeUnit.MINUTES)
    }
    catch (e: InterruptedException) {
      actionCallback.reject(e.message)
    }
    return actionCallback.toPromise()
  }

  protected abstract fun performGC()
}

internal class SystemGCCommand(text: String, line: Int) : AbstractGCCommand(text, line) {
  companion object {
    const val PREFIX = CMD_PREFIX + "performSystemGC"
  }

  override fun performGC() {
    System.gc()
  }
}

internal class JBRFullGCCommand(text: String, line: Int) : AbstractGCCommand(text, line) {
  companion object {
    const val PREFIX = CMD_PREFIX + "performJBRFullGC"
  }

  override fun performGC() {
    if (JBR.isSystemUtilsSupported()) {
      JBR.getSystemUtils().fullGC()
    }
    else {
      System.gc()
    }
  }
}
