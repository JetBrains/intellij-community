// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls

/**
 * The `FreezeUICommand` class represents a command that freezes the user interface for a specified delay.
 *
 * Usage: %freezeUI <duration of freeze in ms>
 */
class FreezeUICommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "freezeUI"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val delay = extractCommandArgument(PREFIX).toLongOrNull()
    if(delay == null){
      throw IllegalArgumentException("Delay parameter shouldn't be null")
    }
    withContext(Dispatchers.EDT){
      Thread.sleep(delay)
    }
  }

  override fun getName(): String = PREFIX


}