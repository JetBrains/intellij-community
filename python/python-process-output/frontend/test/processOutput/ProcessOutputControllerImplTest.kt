package com.intellij.python.processOutput

import com.intellij.python.junit5Tests.framework.applicationScope
import com.intellij.python.processOutput.common.ExecErrorDto
import com.intellij.python.processOutput.common.ExecErrorReasonDto
import com.intellij.python.processOutput.common.ExecutableDto
import com.intellij.python.processOutput.common.FrontendTopicListener
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.common.OutputKindDto
import com.intellij.python.processOutput.common.OutputLineDto
import com.intellij.python.processOutput.common.ProcessBinaryFileName
import com.intellij.python.processOutput.common.ProcessIcon
import com.intellij.python.processOutput.common.ProcessId
import com.intellij.python.processOutput.common.ProcessMatcher
import com.intellij.python.processOutput.common.ProcessOutputEventDto
import com.intellij.python.processOutput.common.TraceContextDto
import com.intellij.python.processOutput.common.TraceContextKind
import com.intellij.python.processOutput.common.TraceContextUuid
import com.intellij.python.processOutput.frontend.LoggedProcess
import com.intellij.python.processOutput.frontend.OutputFilter
import com.intellij.python.processOutput.frontend.ProcessOutputControllerImpl
import com.intellij.python.processOutput.frontend.Limits
import com.intellij.python.processOutput.frontend.ProcessOutputIconMappingData
import com.intellij.python.processOutput.frontend.ProcessStatus
import com.intellij.python.processOutput.frontend.ProcessTreeNode
import com.intellij.python.processOutput.frontend.TreeFilter
import com.intellij.python.processOutput.frontend.childrenOf
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@TestApplication
private class ProcessOutputControllerImplTest {
  @Test
  fun `process limit is maintained`() = runOutputControllerImplTest(10.minutes) {
    // adding MAX_PROCESSES amount of processes
    repeat(Limits.MAX_PROCESSES) {
      addProcessAndAwait(it)
    }

    // the size of logged processes should equal to MAX_PROCESSES
    assertEquals(Limits.MAX_PROCESSES, loggedProcesses.size)

    // adding MAX_PROCESSES * 2 amount of processes
    repeat(Limits.MAX_PROCESSES * 2) {
      addProcessAndAwait(Limits.MAX_PROCESSES + it)
    }

    // the size of logged processes should STILL equal to MAX_PROCESSES
    assertEquals(Limits.MAX_PROCESSES, loggedProcesses.size)
  }

  @Test
  fun `process limits for interactive and background processes are maintained separately`() = runOutputControllerImplTest(10.minutes) {
    // enabling background process filter
    controller.treeSectionState.filters[TreeFilter.Item.SHOW_BACKGROUND_PROCESSES] = true

    // adding MAX_PROCESSES + 10 amount of interactive processes
    repeat(Limits.MAX_PROCESSES + 10) {
      addProcessAndAwait(it)
    }

    // the size of logged processes should equal to MAX_PROCESSES
    assertEquals(Limits.MAX_PROCESSES, loggedProcesses.size)
    assertEquals(Limits.MAX_PROCESSES + 9, loggedProcesses.last().data.id.value, "$loggedProcesses")
    assertEquals(10, loggedProcesses[0].data.id.value)

    // adding MAX_PROCESSES + 10 amount of background processes
    repeat(Limits.MAX_PROCESSES + 10) {
      addProcessAndAwait(it + 100_000, traceContext = nonInteractiveTraceContextDto)
    }

    // the size of logged processes should equal to MAX_PROCESSES * 2
    assertEquals(Limits.MAX_PROCESSES * 2, loggedProcesses.size)
    assertEquals(Limits.MAX_PROCESSES + 9 + 100_000, loggedProcesses.last().data.id.value)
    assertEquals(Limits.MAX_PROCESSES + 9, loggedProcesses[Limits.MAX_PROCESSES - 1].data.id.value)
    assertEquals(10, loggedProcesses[0].data.id.value)

    // adding one interactive and one background process
    addProcessAndAwait(200_000)
    addProcessAndAwait(200_001, traceContext = nonInteractiveTraceContextDto)

    // last two processes should be correct
    assertEquals(200_001, loggedProcesses.last().data.id.value)
    assertEquals(200_000, loggedProcesses.let { it[it.size - 2] }.data.id.value)
  }

  @Test
  fun `line limit is maintained`() = runOutputControllerImplTest(10.minutes) {
    val process = addProcessAndAwait(0)

    // adding MAX_LINES amount of OUT lines
    repeat(Limits.MAX_LINES) {
      process.addOutLine("out$it")
    }

    // should find last added line, and the size of all lines should equal to MAX_LINES
    waitUntil {
      process.lastLine?.let { it.text == "out${Limits.MAX_LINES - 1}" && it.kind == OutputKindDto.OUT } == true
    }
    assertEquals(Limits.MAX_LINES, process.lines.value.size)

    // adding MAX_LINES amount of ERR lines
    repeat(Limits.MAX_LINES) {
      process.addErrLine("err${it + Limits.MAX_LINES}")
    }

    // should find last added line, and the size of all lines should STILL equal to MAX_LINES
    waitUntil {
      process.lastLine?.let { it.text == "err${Limits.MAX_LINES * 2 - 1}" && it.kind == OutputKindDto.ERR } == true
    }
    assertEquals(Limits.MAX_LINES, process.lines.value.size)

    // adding MAX_LINES amount of OUT lines again
    repeat(Limits.MAX_LINES) {
      process.addOutLine("out${it + Limits.MAX_LINES * 2}")
    }

    // should find last added line, and the size of all lines should STILL equal to MAX_LINES
    waitUntil {
      process.lastLine?.let { it.text == "out${Limits.MAX_LINES * 3 - 1}" && it.kind == OutputKindDto.OUT } == true
    }
    assertEquals(Limits.MAX_LINES, process.lines.value.size)
  }

  @Test
  fun `tag section and exit info copy buttons work correctly`() = runOutputControllerImplTest {
    val process = addProcessAndAwait(0)

    repeat(6) {
      process.addOutLine("out$it")
    }

    repeat(4) {
      process.addErrLine("err${it + 6}")
    }

    process.exit(0)

    // copy stdout section 0..5
    controller.copyOutputTagAtIndexToClipboard(process, 0)

    assertEquals(
      """
        out0
        out1
        out2
        out3
        out4
        out5
        
      """.trimIndent(),
      clipboardStrings[0]
    )

    // copy stderr section 6..9
    controller.copyOutputTagAtIndexToClipboard(process, 6)

    Assertions.assertEquals(
      """
        err6
        err7
        err8
        err9
        
      """.trimIndent(),
      clipboardStrings[1],
    )

    // exit info without additional message
    controller.copyOutputExitInfoToClipboard(process)

    Assertions.assertEquals(
      """
        0
        
      """.trimIndent(),
      clipboardStrings[2],
    )

    // exit info with additional message
    process.setAdditionalInfo("some test message")

    waitUntil {
      when (val status = process.status.value) {
        is ProcessStatus.Done -> status.additionalMessageToUser != null
        ProcessStatus.Running -> false
      }
    }

    controller.copyOutputExitInfoToClipboard(process)

    Assertions.assertEquals(
      """
        0: some test message
        
      """.trimIndent(),
      clipboardStrings[3],
    )
  }

  @Test
  fun `toolbar copy includes tags depending on whether the filter is enabled`() = runOutputControllerImplTest {
    val process = addProcessAndAwait(0)

    repeat(6) {
      process.addOutLine("out$it")
    }

    repeat(4) {
      process.addErrLine("err${it + 6}")
    }

    process.exit(0)

    // copying output
    controller.copyOutputToClipboard(process)

    // copied output should include tags
    assertEquals(
      """
        [stdout] out0
                 out1
                 out2
                 out3
                 out4
                 out5
        [stderr] err6
                 err7
                 err8
                 err9
          [exit] 0
        
      """.trimIndent(),
      clipboardStrings[0],
    )

    // toggling the show tags filter
    controller.outputSectionState.filters[OutputFilter.Item.SHOW_TAGS] = false

    // copying output
    controller.copyOutputToClipboard(process)

    // copied output should not include tags
    assertEquals(
      """
        out0
        out1
        out2
        out3
        out4
        out5
        err6
        err7
        err8
        err9
        0
        
      """.trimIndent(),
      clipboardStrings[1]
    )
  }

  @Test
  fun `non-ascii output lines are reflected properly`() = runOutputControllerImplTest {
    val nonAsciiText = "Привет, Мир"
    val asciiText = "Hello, world!"

    val process = addProcessAndAwait(0)
    process.addOutLine(nonAsciiText)
    process.addOutLine(asciiText)

    waitUntil { process.lines.value.size == 2 }

    assertEquals(nonAsciiText, process.lines.value[0].text)
    assertEquals(asciiText, process.lines.value[1].text)
  }

  @Test
  fun `tree is built with nested contexts and root-level processes`() = runOutputControllerImplTest {
    val parentContext = traceContextDto("parent context")
    val childContext = traceContextDto("child context", parentContext.uuid)

    val process1 = addProcessAndAwait(0)
    val process2 = addProcessAndAwait(1, traceContext = parentContext, traceHierarchy = listOf(parentContext))
    val process3 = addProcessAndAwait(2, traceContext = childContext, traceHierarchy = listOf(childContext, parentContext))
    val process4 = addProcessAndAwait(3, traceContext = parentContext, traceHierarchy = listOf(parentContext))

    // wait until root has two expected entries: Context(parentContext) and Process(process1)
    lateinit var rootLevel: List<ProcessTreeNode>
    waitUntil {
      rootLevel = controller.treeSectionState.treeRoot.value
      rootLevel.size >= 2
      && rootLevel.any { it is ProcessTreeNode.Context && it.uuid == parentContext.uuid }
      && rootLevel.any { it is ProcessTreeNode.Process && it.loggedProcess.data.id == process1.data.id }
    }

    // two root items: parentContext and process1
    Assertions.assertEquals(2, rootLevel.size)
    val parentContextNode = rootLevel[0] as ProcessTreeNode.Context
    val process1Node = rootLevel[1] as ProcessTreeNode.Process
    Assertions.assertEquals(parentContext.uuid, parentContextNode.uuid)
    Assertions.assertEquals(process1.data.id, process1Node.loggedProcess.data.id)

    // parentContext's children: process4, childContext, process2
    val parentContextChildren = parentContextNode.childrenOf<ProcessTreeNode>()
    Assertions.assertEquals(3, parentContextChildren.size)
    Assertions.assertEquals(process4.data.id, (parentContextChildren[0] as ProcessTreeNode.Process).loggedProcess.data.id)
    val childContextNode = parentContextChildren[1] as ProcessTreeNode.Context
    Assertions.assertEquals(childContext.uuid, childContextNode.uuid)
    Assertions.assertEquals(process2.data.id, (parentContextChildren[2] as ProcessTreeNode.Process).loggedProcess.data.id)

    // childContext's children: process3
    val childContextChildren = childContextNode.childrenOf<ProcessTreeNode>()
    Assertions.assertEquals(1, childContextChildren.size)
    Assertions.assertEquals(process3.data.id, (childContextChildren[0] as ProcessTreeNode.Process).loggedProcess.data.id)
  }

  @Test
  fun `tree is rebuilt on search query change`() = runOutputControllerImplTest {
    val pythonProcess = addProcessAndAwait(0, exeParts = listOf("testpython.py"))
    val nodeProcess = addProcessAndAwait(1, exeParts = listOf("testnode.py"))
    val cargoProcess = addProcessAndAwait(2, exeParts = listOf("testcargo.py"))

    val processIdsInTree = {
      controller.treeSectionState.treeRoot.value
        .filterIsInstance<ProcessTreeNode.Process>()
        .map { it.loggedProcess.data.id }
        .toSet()
    }

    // default empty search: all three processes are visible
    waitUntil { processIdsInTree() == setOf(pythonProcess.data.id, nodeProcess.data.id, cargoProcess.data.id) }

    // "python" matches only the python exe
    controller.search("testpython")
    waitUntil { processIdsInTree() == setOf(pythonProcess.data.id) }

    // search is case-insensitive: "NODE" still matches the node exe
    controller.search("TESTNODE")
    waitUntil { processIdsInTree() == setOf(nodeProcess.data.id) }

    // substring match: "estcarg" is a substring of "testcargo" only
    controller.search("estcarg")
    waitUntil { processIdsInTree() == setOf(cargoProcess.data.id) }

    // tree is empty when no matches were found
    controller.search("nothingatall")
    waitUntil { processIdsInTree().isEmpty() }

    // clearing the query brings every process back
    controller.search("")
    waitUntil {
      processIdsInTree() == setOf(pythonProcess.data.id, nodeProcess.data.id, cargoProcess.data.id)
    }
  }

  @Test
  fun `tree hides background processes when SHOW_BACKGROUND_PROCESSES filter is toggled`() = runOutputControllerImplTest {
    val backgroundContext = nonInteractiveTraceContextDto
    val interactiveContext = traceContextDto("interactive context")

    addProcess(0, traceContext = backgroundContext)
    addProcessAndAwait(1, traceContext = interactiveContext)

    val nodeIdsInTree = {
      controller.treeSectionState.treeRoot.value
        .map { it.nodeId }
        .toSet()
    }

    // by default, background processes are hidden
    waitUntil {
      nodeIdsInTree() == setOf(interactiveContext.treeId)
    }

    // enabling the filter makes background processes visible
    controller.treeSectionState.filters[TreeFilter.Item.SHOW_BACKGROUND_PROCESSES] = true
    waitUntil {
      nodeIdsInTree() == setOf(interactiveContext.treeId, ProcessTreeNode.Id.Process(ProcessId(0)))
    }

    // disabling it again hides the background process
    controller.treeSectionState.filters[TreeFilter.Item.SHOW_BACKGROUND_PROCESSES] = false
    waitUntil {
      nodeIdsInTree() == setOf(interactiveContext.treeId)
    }
  }

  companion object {
    val testScope = applicationScope("ProcessOutputControllerImplTestScope")

    fun createProcessDto(
      id: Int,
      exeParts: List<String> = listOf("bin", "exe"),
      traceContext: TraceContextUuid? = null,
    ) =
      LoggedProcessDto(
        weight = null,
        traceContextUuid = traceContext,
        pid = null,
        startedAt = Instant.fromEpochMilliseconds(id.toLong()),
        cwd = null,
        exe = ExecutableDto(
          path = exeParts.joinToString("/"),
          parts = exeParts,
        ),
        args = emptyList(),
        env = emptyMap(),
        target = "",
        id = ProcessId(id),
      )

    private class TestContext(
      val controller: ProcessOutputControllerImpl,
      private val eventsFlow: MutableSharedFlow<ProcessOutputEventDto>,
      val clipboardStrings: List<String>,
    ) {
      val LoggedProcess.lastLine
        get() =
          lines.value.lastOrNull()

      suspend fun addProcess(
        id: Int,
        exeParts: List<String> = listOf("bin", "exe"),
        traceContext: TraceContextDto? = null,
        traceHierarchy: List<TraceContextDto> = traceContext?.let { listOf(it) } ?: emptyList(),
      ) {
        emitFeEvent(
          ProcessOutputEventDto.NewProcess(
            loggedProcess = createProcessDto(id, exeParts = exeParts, traceContext = traceContext?.uuid),
            traceHierarchy = traceHierarchy
          )
        )
      }

      suspend fun addProcessAndAwait(
        id: Int,
        exeParts: List<String> = listOf("bin", "exe"),
        traceContext: TraceContextDto? = null,
        traceHierarchy: List<TraceContextDto> = traceContext?.let { listOf(it) } ?: emptyList(),
      ): LoggedProcess {
        addProcess(id, exeParts, traceContext, traceHierarchy)

        lateinit var loggedProcess: LoggedProcess

        waitUntil {
          loggedProcess = loggedProcesses.findLast { it.data.id.value == id } ?: return@waitUntil false

          true
        }

        return loggedProcess
      }

      suspend fun LoggedProcess.addOutLine(text: String) {
        emitFeEvent(
          ProcessOutputEventDto.NewOutputLine(
            processId = data.id,
            outputLine =
              OutputLineDto(
                kind = OutputKindDto.OUT,
                text = text
              )
          )
        )
      }

      suspend fun LoggedProcess.setAdditionalInfo(text: String) {
        emitFeEvent(
          ProcessOutputEventDto.ExecError(
            ExecErrorDto(
              message = "",
              command = "",
              reason = ExecErrorReasonDto.Timeout,
              loggedProcessId = data.id,
              additionalMessageToUser = text
            )
          )
        )
      }

      suspend fun LoggedProcess.addErrLine(text: String) {
        emitFeEvent(
          ProcessOutputEventDto.NewOutputLine(
            processId = data.id,
            outputLine =
              OutputLineDto(
                kind = OutputKindDto.ERR,
                text = text
              )
          )
        )
      }

      suspend fun LoggedProcess.exit(exitCode: Int, exitedAt: Instant = Instant.fromEpochMilliseconds(0)) {
        emitFeEvent(
          ProcessOutputEventDto.ProcessExit(
            processId = data.id,
            exitedAt = exitedAt,
            exitValue = exitCode
          )
        )
      }

      private suspend fun emitFeEvent(event: ProcessOutputEventDto) {
        testScope.get().async { eventsFlow.emit(event) }.await()
      }

      private fun List<ProcessTreeNode>.recurse(callback: (ProcessTreeNode) -> Unit) {
        for (node in this) {
          callback(node)
          when (node) {
            is ProcessTreeNode.Context -> {
              node.childrenOf<ProcessTreeNode>().recurse(callback)
            }
            is ProcessTreeNode.Process -> {}
          }
        }
      }

      val loggedProcesses: List<LoggedProcess>
        get() {
          val processes = mutableListOf<LoggedProcess>()

          controller.treeSectionState.treeRoot.value.recurse {
            when (it) {
              is ProcessTreeNode.Process -> {
                processes += it.loggedProcess
              }
              is ProcessTreeNode.Context -> {}
            }
          }

          return processes.reversed()
        }
    }

    private fun runOutputControllerImplTest(
      timeout: Duration = 10.seconds,
      testBody: suspend TestContext.() -> Unit,
    ) =
      timeoutRunBlocking(timeout) {
        val eventsFlow = MutableSharedFlow<ProcessOutputEventDto>()
        val copiedStrings = mutableListOf<String>()
        val controller = ProcessOutputControllerImpl(
          coroutineScope = testScope.get(),
          frontendTopic =
            object : FrontendTopicListener {
              override val events: Flow<ProcessOutputEventDto> = eventsFlow
            },
          iconMappingData =
            object : ProcessOutputIconMappingData {
              override val mapping: Map<ProcessBinaryFileName, ProcessIcon> = emptyMap()
              override val matchers: List<ProcessMatcher> = emptyList()
            },
          usageCollector = {},
          clipboardCopier = { copiedStrings += it }
        )

        TestContext(controller, eventsFlow, copiedStrings).testBody()
      }

    private fun traceContextDto(title: String, parentUuid: TraceContextUuid? = null) =
      TraceContextDto(
        title = title,
        timestamp = 0,
        uuid = TraceContextUuid(UUID.randomUUID().toString()),
        kind = TraceContextKind.INTERACTIVE,
        parentUuid = parentUuid
      )

    private val nonInteractiveTraceContextDto: TraceContextDto =
      TraceContextDto(
        title = "non interactive",
        timestamp = 0,
        uuid = TraceContextUuid("aaa-bbb-ccc"),
        kind = TraceContextKind.NON_INTERACTIVE,
        parentUuid = null,
      )

    private val TraceContextDto.treeId
      get() =
        ProcessTreeNode.Id.Context(uuid)

    private val LoggedProcess.treeId
      get() =
        ProcessTreeNode.Id.Process(data.id)
  }
}