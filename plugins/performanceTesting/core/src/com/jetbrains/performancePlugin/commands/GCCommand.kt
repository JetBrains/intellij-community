package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.ActionCallback
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

internal class GCCommand(text: String, line: Int) : AbstractCommand(text, line) {

  companion object {
    const val PREFIX = CMD_PREFIX + "performGC"
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback: ActionCallback = ActionCallbackProfilerStopper()
    val ref = WeakReference(IntArray(32 * 1024))
    try {
      Waiter.checkCondition {
        System.gc()
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
}