package com.intellij.python.processOutput

import com.intellij.python.processOutput.common.ExecutableDto
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.common.OutputLineDto
import com.intellij.python.processOutput.common.ProcessId
import com.intellij.python.processOutput.common.ProcessWeightDto
import com.intellij.python.processOutput.common.TraceContextDto
import com.intellij.python.processOutput.common.TraceContextKind
import com.intellij.python.processOutput.common.TraceContextUuid
import com.intellij.python.processOutput.frontend.LoggedProcess
import com.intellij.python.processOutput.frontend.ProcessStatus
import com.intellij.python.processOutput.frontend.ProcessTreeNode
import com.intellij.python.processOutput.frontend.formatTime
import com.intellij.python.processOutput.frontend.ui.shortenedCommandString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

internal class ProcessTreeNodeTest {
  @Test
  fun `context fields resolve to correct values`() {
    val title = "test context"
    val timestamp = 10_000L
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val contextUuid = TraceContextUuid("xxx-yyy-zzz")
    val node =
      createTraceContextNode(
        title = title,
        timestamp = timestamp,
      )

    // context node's fields resolve to correct values
    assertEquals(title, node.title)
    assertEquals(instant, node.timestamp)
    assertEquals(instant.formatTime(), node.formattedTimestamp)
    assertEquals(contextUuid, node.nodeId.traceContextUuid)
    assertEquals(contextUuid, node.uuid)
  }

  @Test
  fun `process basic fields resolve to correct values`() {
    val id = 6767
    val instant = Instant.fromEpochMilliseconds(111_000)
    val weight = ProcessWeightDto.HEAVY
    val (node, _) =
      createProcessNode(
        processId = id,
        instant = instant,
        weight = weight,
      )

    // process node's basic fields resolve to correct values
    assertEquals(false, node.isBackground)
    assertEquals(null, node.processIcon)
    assertEquals(node.loggedProcess.data.shortenedCommandString, node.title)
    assertEquals(instant, node.timestamp)
    assertEquals(instant.formatTime(), node.formattedTimestamp)
    assertEquals(id, node.nodeId.processId.value)
    assertEquals(weight, node.weight)
  }

  @Test
  fun `process node isRunning field resolves to correct value`() {
    val (node, statusFlow) = createProcessNode()

    // isRunning should be true
    assertEquals(true, node.isRunning)

    // setting process status to done
    statusFlow.value = createDoneStatus(0)

    // isRunning should be false
    assertEquals(false, node.isRunning)
  }

  @Test
  fun `process node's error fields resolve to correct values`() {
    val (node, statusFlow) = createProcessNode()

    // isCriticalError should be false, isError should be false
    assertEquals(false, node.isCriticalError)
    assertEquals(false, node.isError)

    // exit code 1, criticalError set to false
    statusFlow.value = createDoneStatus(exitCode = 1, isCritical = false)

    // isCriticalError should be false, isError should be true
    assertEquals(false, node.isCriticalError)
    assertEquals(true, node.isError)

    // exit code 1, criticalError set to true
    statusFlow.value = createDoneStatus(exitCode = 1, isCritical = true)

    // isCriticalError should be false, isError should be true
    assertEquals(true, node.isCriticalError)
    assertEquals(true, node.isError)
  }

  companion object {
    fun createTraceContextNode(
      title: String,
      timestamp: Long = 0,
      traceContextUuid: TraceContextUuid = TraceContextUuid("xxx-yyy-zzz"),
    ): ProcessTreeNode.Context =
      ProcessTreeNode.Context(
        TraceContextDto(
          title = title,
          timestamp = timestamp,
          uuid = traceContextUuid,
          kind = TraceContextKind.INTERACTIVE,
          parentUuid = null
        )
      )

    fun createProcessNode(
      exeParts: List<String> = listOf("/", "usr", "bin", "exe"),
      args: List<String> = listOf("arg1", "arg2", "arg3"),
      processId: Int = 0,
      instant: Instant = Instant.fromEpochMilliseconds(0),
      weight: ProcessWeightDto? = null,
      isBackground: Boolean = false,
    ): Pair<ProcessTreeNode.Process, MutableStateFlow<ProcessStatus>> {
      val statusFlow = MutableStateFlow<ProcessStatus>(ProcessStatus.Running)
      val processNode =
        ProcessTreeNode.Process(
          loggedProcess =
            object : LoggedProcess {
              override val data =
                LoggedProcessDto(
                  weight = weight,
                  traceContextUuid = null,
                  pid = 0,
                  startedAt = instant,
                  cwd = null,
                  exe = ExecutableDto(
                    exeParts.joinToString("/"),
                    exeParts,
                  ),
                  args = args,
                  env = emptyMap(),
                  target = "",
                  id = ProcessId(processId),
                )
              override val lines: StateFlow<List<OutputLineDto>> = MutableStateFlow(emptyList())
              override val status = statusFlow
            },
          isBackground = isBackground,
          processIcon = null
        )

      return processNode to statusFlow
    }

    fun createDoneStatus(
      exitCode: Int = 0,
      isCritical: Boolean = false,
    ): ProcessStatus.Done =
      ProcessStatus.Done(
        exitedAt = Instant.fromEpochMilliseconds(0),
        exitCode = exitCode,
        isCritical = isCritical,
      )
  }
}
