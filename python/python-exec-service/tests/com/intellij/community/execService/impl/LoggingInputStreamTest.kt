// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.community.execService.impl

import com.intellij.python.community.execService.impl.LoggingInputStream
import com.intellij.python.community.execService.impl.LoggingLimits
import com.intellij.python.processOutput.common.ExecErrorDto
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.common.OutputKindDto
import com.intellij.python.processOutput.common.OutputLineDto
import com.intellij.python.processOutput.common.ProcessId
import com.intellij.python.processOutput.common.ProcessOutputTopicSender
import com.intellij.python.processOutput.common.TraceContextDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Instant

internal class LoggingInputStreamTest {
  @Test
  fun `logging input stream emits correct events`() {
    val outInput = "hello, world!\n"
    val outProcessId = 10_000
    val (outStream, outNewLineEvents) = createInputStream(outInput, outProcessId, OutputKindDto.OUT)

    outStream.readAllBytes()

    assertEquals(1, outNewLineEvents.size)
    
    outNewLineEvents[0].let { event ->
      assertEquals(outProcessId, event.processId.value)
      assertEquals(OutputKindDto.OUT, event.kind)
      assertEquals(outInput.trim(), event.text)
    }

    val errInput = "see you, world...\n"
    val errProcessId = 10_000
    val (errStream, errNewLineEvents) = createInputStream(errInput, errProcessId, OutputKindDto.ERR)

    errStream.readAllBytes()

    assertEquals(1, errNewLineEvents.size)
    
    errNewLineEvents[0].let { event ->
      assertEquals(errProcessId, event.processId.value)
      assertEquals(OutputKindDto.ERR, event.kind)
      assertEquals(errInput.trim(), event.text)
    }
  }

  @Test
  fun `max output size is enforced`() {
    val input = "x".repeat(LoggingLimits.MAX_OUTPUT_SIZE * 2)
    val (stream, newLineEvents) = createInputStream(input, 0, OutputKindDto.OUT)

    stream.readAllBytes()

    assertEquals(LoggingLimits.MAX_OUTPUT_SIZE, newLineEvents[0].text.length)
  }

  @Test
  fun `lines are handled correctly`() {
    val input = "line1\nline2\nline3\r\r\nline4\nline5\n"
    val (stream, newLineEvents) = createInputStream(input, 0, OutputKindDto.OUT)

    // no reads were made, no events should have been emitted
    assertEquals(0, newLineEvents.size)

    // reading the next 6 bytes, consume "line1" and the newline character
    stream.readNBytes(6)

    // a new line event should have been emitted
    assertEquals(1, newLineEvents.size)
    assertEquals("line1", newLineEvents[0].text)

    // reading the next 5 bytes, consume "line2" but NOT the newline character
    stream.readNBytes(5)

    // no additional new line events should have been emitted, since no newline character was consumed
    assertEquals(1, newLineEvents.size)

    // consuming the newline character
    stream.readNBytes(1)

    // the newline character was consumed, an additional event should have been emitted
    assertEquals(2, newLineEvents.size)
    assertEquals("line2", newLineEvents[1].text)

    // consume next 6 bytes, "line3" and a carriage return character
    stream.readNBytes(6)

    // no additional new line events should have been emitted, since a carriage return character is not a newline character
    assertEquals(2, newLineEvents.size)

    // consume the next carriage return character
    stream.readNBytes(1)

    // no additional event is expected yet again
    assertEquals(2, newLineEvents.size)

    // consuming the newline character
    stream.readNBytes(1)

    // new event should have been emitted, and the text line should not contain any carriage return characters
    assertEquals(3, newLineEvents.size)
    assertEquals("line3", newLineEvents[2].text)

    // consuming the rest
    stream.readAllBytes()

    // 2 new events should have been emitted
    assertEquals(5, newLineEvents.size)
    assertEquals("line4", newLineEvents[3].text)
    assertEquals("line5", newLineEvents[4].text)
  }

  @Test
  fun `unexpected closure is handled correctly`() {
    val input = "line1\nline2\nline3\nline4\n"
    val (stream, newLineEvents) = createInputStream(input, 0, OutputKindDto.OUT)

    // consume two lines
    stream.readNBytes(12)

    // 2 events should have been emitted
    assertEquals(2, newLineEvents.size)
    assertEquals("line1", newLineEvents[0].text)
    assertEquals("line2", newLineEvents[1].text)

    // read up to the middle of the next line, then close the stream
    stream.readNBytes(3)
    stream.close()

    // an event with half the line should have been emitted
    assertEquals(3, newLineEvents.size)
    assertEquals("lin", newLineEvents[2].text)

    // subsequent reads return -1
    assertEquals(-1, stream.read())
  }

  @Test
  fun `end of input is treated like a newline`() {
    val input = "line1\nline2"
    val (stream, newLineEvents) = createInputStream(input, 0, OutputKindDto.OUT)

    // consume the entire stream
    stream.readAllBytes()

    // 2 events should have been emitted
    assertEquals(2, newLineEvents.size)
    assertEquals("line1", newLineEvents[0].text)
    assertEquals("line2", newLineEvents[1].text)
  }

  @Test
  fun `double close doesn't result in duplicate events`() {
    val input = "line1\nline2"
    val (stream, newLineEvents) = createInputStream(input, 0, OutputKindDto.OUT)

    // consume half of the first line, then close the stream
    stream.readNBytes(3)
    stream.close()

    // one event should have been emitted
    assertEquals(1, newLineEvents.size)
    assertEquals("lin", newLineEvents[0].text)

    // calling close again
    stream.close()

    // no additional events should have been emitted
    assertEquals(1, newLineEvents.size)
  }

  data class TestOutputLineDto(val processId: ProcessId, val kind: OutputKindDto, val text: String)

  companion object {
    private fun createInputStream(
      backingInput: String,
      processId: Int,
      kind: OutputKindDto,
    ): Pair<LoggingInputStream, List<TestOutputLineDto>> {
      val newLineEvents = mutableListOf<TestOutputLineDto>()
      val loggingInputStream =
        LoggingInputStream(
          processId = ProcessId(processId),
          backingInputStream = backingInput.byteInputStream(),
          kind = kind,
          topicSender = object : ProcessOutputTopicSender {
            override fun sendNewProcessEvent(loggedProcessDto: LoggedProcessDto, traceHierarchy: List<TraceContextDto>) {
              unexpectedMethodCalledException()
            }

            override fun sendNewOutputLineEvent(processId: ProcessId, outputLine: OutputLineDto) {
              newLineEvents += TestOutputLineDto(processId, outputLine.kind, outputLine.text)
            }

            override fun sendProcessExitEvent(processId: ProcessId, exitedAt: Instant, exitValue: Int) {
              unexpectedMethodCalledException()
            }

            override fun sendExecErrorEvent(execErrorDto: ExecErrorDto) {
              unexpectedMethodCalledException()
            }

            override fun sendOpenToolWindowByTraceUuidEvent(uuid: UUID, openIfNotFound: Boolean) {
              unexpectedMethodCalledException()
            }

            override fun sendOpenToolWindowByTraceUuidEvent(uuid: String, openIfNotFound: Boolean) {
              unexpectedMethodCalledException()
            }

            fun unexpectedMethodCalledException() {
              throw IllegalStateException("unexpected method called")
            }
          }
        )

      return loggingInputStream to newLineEvents
    }
  }
}
