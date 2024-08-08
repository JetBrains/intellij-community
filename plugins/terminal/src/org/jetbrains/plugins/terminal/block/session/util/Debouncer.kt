// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session.util

import com.intellij.openapi.Disposable
import com.intellij.util.Alarm
import java.util.concurrent.atomic.AtomicBoolean

internal class Debouncer(
  private val delay: Int,
  parentDisposable: Disposable,
) {
  private val scheduled: AtomicBoolean = AtomicBoolean(false)
  private val alarm: Alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable)

  @Volatile
  private var useExtendedDelayOnce: Boolean = false

  fun setExtendedDelayOnce() {
    useExtendedDelayOnce = true
  }

  fun execute(action: () -> Unit) {
    if (scheduled.compareAndSet(false, true)) {
      val request = {
        scheduled.set(false)
        action()
      }
      val delay = if (useExtendedDelayOnce) 100 + delay else delay
      useExtendedDelayOnce = false
      if (delay <= 0) {
        request.invoke()
      }
      else {
        alarm.addRequest(request, delay)
      }
    }
  }
}
