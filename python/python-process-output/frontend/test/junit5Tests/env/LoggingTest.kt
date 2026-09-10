// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env

import com.intellij.python.processOutput.common.ExecutableDto
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.common.OutputLineDto
import com.intellij.python.processOutput.common.ProcessId
import com.intellij.python.processOutput.frontend.LoggedProcess
import com.intellij.python.processOutput.frontend.ProcessStatus
import com.intellij.python.processOutput.frontend.ui.commandString
import com.intellij.python.processOutput.frontend.ui.shortenedCommandString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock

internal class LoggedProcessTest {
  @Test
  fun `commandString is constructed as expected`() {
    val process1 = process("/usr/bin/uv", "install", "requests")

    assertEquals("/usr/bin/uv install requests", process1.data.commandString)
  }

  @Test
  fun `shortenedCommandString is constructed as expected from multiple segments`() {
    val process1 = process("/usr/bin/uv", "install", "requests")

    assertEquals("uv install requests", process1.data.shortenedCommandString)
  }

  @Test
  fun `shortenedCommandString is constructed as expected from single segment`() {
    val process1 = process("uv", "install", "requests")

    assertEquals("uv install requests", process1.data.shortenedCommandString)
  }

  companion object {
    val nextId = AtomicInteger()

    fun process(vararg command: String) =
      object : LoggedProcess {
        override val data = LoggedProcessDto(
          weight = null,
          traceContextUuid = null,
          pid = 123,
          startedAt = Clock.System.now(),
          cwd = null,
          exe = ExecutableDto(
            path = command.first(),
            parts = command.first().split(Regex("[/\\\\]+")),
          ),
          args = command.drop(1),
          env = mapOf(),
          target = "Local",
          id = ProcessId(nextId.getAndAdd(1)),
        )
        override val lines: StateFlow<List<OutputLineDto>> = MutableStateFlow(emptyList())
        override val status = MutableStateFlow(ProcessStatus.Running)
      }
  }
}
