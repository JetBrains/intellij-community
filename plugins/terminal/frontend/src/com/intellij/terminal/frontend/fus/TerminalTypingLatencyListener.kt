// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.fus

import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TerminalTypingLatencyListener {
  companion object {
    @Topic.AppLevel
    @JvmField
    val TOPIC: Topic<TerminalTypingLatencyListener> = Topic(TerminalTypingLatencyListener::class.java, Topic.BroadcastDirection.NONE)
  }

  /**
   * Records the latency between pressing the key and the moment its effect is painted in [terminalView].
   */
  fun recordTypingLatency(terminalView: TerminalView, latencyMs: Long)
}
