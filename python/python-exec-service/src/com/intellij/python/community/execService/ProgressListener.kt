// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService

import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.annotations.ApiStatus

/**
 * Listens for start/stop/std{out,err} events
 */
typealias PyProcessListener = FlowCollector<ProcessEvent>

sealed interface ProcessEvent {
  data class ProcessStarted @ApiStatus.Internal constructor(val binary: BinaryToExec, val args: List<String>) : ProcessEvent
  data class ProcessOutput @ApiStatus.Internal constructor(val stream: OutputType, val line: String) : ProcessEvent
  data class ProcessEnded @ApiStatus.Internal constructor(val exitCode: Int) : ProcessEvent

  enum class OutputType {
    STDOUT, STDERR
  }
}