// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.performancePlugin.commands

import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import org.jetbrains.concurrency.Promise

class RecordCounterCollectorBaselinesCommand(text: String, line: Int) : AbstractCommand(text, line) {
  override fun _execute(context: PlaybackContext): Promise<Any?> {
    return FUCounterUsageLogger.getInstance().logRegisteredGroups().toPromise()
  }

  companion object {
    const val PREFIX = CMD_PREFIX + "recordRegisteredCounterGroups"
  }
}