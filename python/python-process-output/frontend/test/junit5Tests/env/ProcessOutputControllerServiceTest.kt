package com.intellij.python.junit5Tests.env

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.impl.LoggingLimits
import com.intellij.python.community.execService.impl.LoggingProcess
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.processOutput.common.ExecutableDto
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.common.OutputKindDto
import com.intellij.python.processOutput.common.OutputLineDto
import com.intellij.python.processOutput.common.ProcessOutputEventDto
import com.intellij.python.processOutput.common.TraceContextDto
import com.intellij.python.processOutput.common.TraceContextKind
import com.intellij.python.processOutput.common.TraceContextUuid
import com.intellij.python.processOutput.common.sendProcessOutputTopicEvent
import com.intellij.python.processOutput.frontend.CoroutineNames
import com.intellij.python.processOutput.frontend.LoggedProcess
import com.intellij.python.processOutput.frontend.OutputFilter
import com.intellij.python.processOutput.frontend.ProcessOutputController
import com.intellij.python.processOutput.frontend.ProcessOutputControllerService
import com.intellij.python.processOutput.frontend.ProcessOutputControllerServiceLimits
import com.intellij.python.processOutput.frontend.ProcessStatus
import com.intellij.python.processOutput.frontend.ProcessTreeNode
import com.intellij.python.processOutput.frontend.TreeFilter
import com.intellij.python.processOutput.frontend.ui.commandString
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.io.awaitExit
import com.intellij.util.system.OS
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrThrow
import java.awt.datatransfer.DataFlavor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.io.TempDir
import java.util.concurrent.ConcurrentLinkedQueue

@PyEnvTestCase
class ProcessOutputControllerServiceTest {
  private val projectFixture = projectFixture()

  @Test
  fun `stress and limits test`(
    @TempDir cwd: Path,
    @PythonBinaryPath python: PythonBinary,
  ): Unit = timeoutRunBlocking(15.minutes) {
    val service = projectFixture.get().service<ProcessOutputControllerService>()
    val newLineLen = if (OS.CURRENT == OS.Windows) 2 else 1
    val binOnEel = BinOnEel(python, cwd)
    val mainPy = Files.createFile(cwd.resolve(MAIN_PY))

    fun verifyCurrentProcesses(over: Int) {
      var count = 0
      val processes = service.loggedProcesses.value

      for ((_, lines, status) in processes) {
        if (lines.value.isEmpty()
            || lines.value[0].kind != OutputKindDto.OUT
            || !lines.value[0].text.startsWith("test ")) {
          continue
        }

        with(lines.value) {
          assertEquals(
            3,
            size,
            buildString {
              appendLine("Test process output is expected to have 3 lines, but was $size.")
              appendLine("Lines preview:")
              append(lines.value.generatePreview(10))
            }
          )

          val testNumber = get(0).text.split(" ")[1].toInt()

          assert(testNumber >= over) {
            "Test process header ('test x') is expected to hold a number greater or equal to $over, but was $testNumber."
          }

          val xLen =
            LoggingLimits.MAX_OUTPUT_SIZE - (get(0).text.length + newLineLen)
          val yLen = LoggingLimits.MAX_OUTPUT_SIZE

          assertNotNull(
            find { elem ->
              elem.text.startsWith("xxx") && elem.text.length == xLen
            },
            buildString {
              appendLine("Test process output is expected to have a string of 'x' repeating $xLen amount of times.")
              appendLine("Lines preview:")
              append(lines.value.generatePreview(10))
            }
          )
          assertNotNull(
            find { elem ->
              elem.text.startsWith("yyy") && elem.text.length == yLen
            },
            buildString {
              appendLine("Test process output is expected to have a string of 'y' repeating $yLen amount of times.")
              appendLine("Lines preview:")
              append(lines.value.generatePreview(10))
            }
          )
        }

        with(status.value) {
          assert(this is ProcessStatus.Done && exitCode == 0)
        }

        count++
      }

      val processesToCheck = ProcessOutputControllerServiceLimits.MAX_PROCESSES - 30

      // should expect to have found and asserted MAX_PROCESSES amount processes
      // 30 for margin of error
      assert(count > processesToCheck) {
        buildString {
          appendLine("Call to `verifyCurrentProcesses` is expected to check at least $processesToCheck processes, but checked only $count.")

          for ((index, process) in processes.withIndex()) {
            appendLine("Process $index: ${process.data.commandString}")
          }
        }
      }
    }

    edtWriteAction {
      mainPy.toFile().writeText(
        """
          import sys 
          
          print("test " + sys.argv[1])
          print("${"x".repeat(LoggingLimits.MAX_OUTPUT_SIZE * 2)}")
          print("${"y".repeat(LoggingLimits.MAX_OUTPUT_SIZE * 2)}", file=sys.stderr)
        """.trimIndent(),
      )
    }

    // executing the file MAX_PROCESSES amount of times
    repeat(ProcessOutputControllerServiceLimits.MAX_PROCESSES) {
      runBin(binOnEel, Args(MAIN_PY, it.toString()))
    }

    // the amount of processes logged should exactly equal to MAX_PROCESSES
    waitUntil {
      service.loggedProcesses.value.size == ProcessOutputControllerServiceLimits.MAX_PROCESSES
    }

    // should have verified processes 0 to MAX_PROCESSES - 1
    verifyCurrentProcesses(0)

    // adding processes 2 times over the limit
    repeat(ProcessOutputControllerServiceLimits.MAX_PROCESSES * 2) {
      val newIt = (it + ProcessOutputControllerServiceLimits.MAX_PROCESSES)

      runBin(binOnEel, Args(MAIN_PY, newIt.toString()))
    }

    // older processes beyond MAX_PROCESSES should be truncated
    assertEquals(
      ProcessOutputControllerServiceLimits.MAX_PROCESSES,
      service.loggedProcesses.value.size,
    )

    // should have verified processes MAX_PROCESSES to MAX_PROCESSES * 2 - 1
    verifyCurrentProcesses(ProcessOutputControllerServiceLimits.MAX_PROCESSES * 2)
  }

  @Test
  fun `exit info collector coroutines get properly cleaned up`(
    @TempDir cwd: Path,
    @PythonBinaryPath python: PythonBinary,
  ): Unit = timeoutRunBlocking(15.minutes) {
    projectFixture.get().service<ProcessOutputControllerService>()

    val binOnEel = BinOnEel(python, cwd)
    val mainPy = Files.createFile(cwd.resolve(MAIN_PY))

    edtWriteAction {
      mainPy.toFile().writeText(
        """
          import sys 
          
          print("test " + sys.argv[1])
          sys.stdin.read(1)
        """.trimIndent(),
      )
    }

    // no coroutines should be active (5 for margin of error)
    assert(exitInfoCollectorCoroutinesCount() < 5)

    // spawn 1024 processes, instantly terminate them
    repeat(1024) {
      val process = runBinWithInput(binOnEel, Args(MAIN_PY, it.toString()))
      inputAndAwaitExit(process)
    }

    // no coroutines should be active (5 for margin of error)
    waitUntil {
      exitInfoCollectorCoroutinesCount() < 5
    }

    // spawn 100 processes
    val processes = mutableListOf<Process>()
    repeat(100) {
      processes += runBinWithInput(binOnEel, Args(MAIN_PY, it.toString()))
    }

    // at least 100 coroutines should be active
    waitUntil {
      exitInfoCollectorCoroutinesCount() >= 100
    }

    // but not more than 105
    assert(exitInfoCollectorCoroutinesCount() <= 105)

    // terminating all processes
    for (process in processes) {
      inputAndAwaitExit(process)
    }

    // updating the flow by adding and terminating one process
    val process = runBinWithInput(binOnEel, Args(MAIN_PY, "500"))
    inputAndAwaitExit(process)

    // no coroutines should be active (5 for margin of error)
    waitUntil {
      exitInfoCollectorCoroutinesCount() < 5
    }
  }

  @Test
  fun `tag section and exit info copy buttons test`(
    @TempDir cwd: Path,
    @PythonBinaryPath python: PythonBinary,
  ): Unit = timeoutRunBlocking {
    val service = projectFixture.get().service<ProcessOutputControllerService>()

    val binOnEel = BinOnEel(python, cwd)
    val mainPy = Files.createFile(cwd.resolve(MAIN_PY))

    edtWriteAction {
      mainPy.toFile().writeText(
        """
          import sys 
          
          print("out1")
          print("out2")
          print("out3")
          print("out4")
          print("out5")
          print("out6")
          
          print("err7", file=sys.stderr)
          print("err8", file=sys.stderr)
          print("err9", file=sys.stderr)
          print("err10", file=sys.stderr)
        """.trimIndent(),
      )
    }

    val loggingProcess = withContext(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
      ExecService().executeGetProcess(
        binOnEel,
        Args(MAIN_PY),
        CoroutineScope(coroutineContext),
      ).getOrThrow()
    }

    // reading all stdout
    loggingProcess.inputStream.readAllBytes()

    waitUntil {
      service.loggedProcesses.value.lastOrNull()?.lines?.value?.size == 6
    }

    val process = service.loggedProcesses.value.last()

    // stdout section (0..5)
    service.copyOutputTagAtIndexToClipboard(process, 0)

    assertEquals(
      """
        out1
        out2
        out3
        out4
        out5
        out6
        
      """.trimIndent(),
      CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor),
    )

    // reading all stderr
    loggingProcess.errorStream.readAllBytes()

    waitUntil { process.lines.value.size == 10 }

    // stderr section (6..9)
    service.copyOutputTagAtIndexToClipboard(process, 6)

    assertEquals(
      """
        err7
        err8
        err9
        err10
        
      """.trimIndent(),
      CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor),
    )

    // exit info without additional message
    service.copyOutputExitInfoToClipboard(process)

    assertEquals(
      """
        0
        
      """.trimIndent(),
      CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor),
    )

    // exit info with additional message
    val status = process.status.value as ProcessStatus.Done

    (process.status as MutableStateFlow<ProcessStatus>).emit(
      status.copy(
        additionalMessageToUser = "some test message",
      ),
    )

    service.copyOutputExitInfoToClipboard(process)

    assertEquals(
      """
                0: some test message
                
            """.trimIndent(),
      CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor),
    )
  }

  @Test
  fun `toolbar copy includes tags depending on whether the filter is enabled`(
    @TempDir cwd: Path,
    @PythonBinaryPath python: PythonBinary,
  ): Unit = timeoutRunBlocking {
    val service = projectFixture.get().service<ProcessOutputControllerService>()

    val binOnEel = BinOnEel(python, cwd)
    val mainPy = Files.createFile(cwd.resolve(MAIN_PY))

    edtWriteAction {
      mainPy.toFile().writeText(
        """
          import sys 
          
          print("out1")
          print("out2")
          print("out3")
          print("out4")
          print("out5")
          print("out6")
          
          print("err7", file=sys.stderr)
          print("err8", file=sys.stderr)
          print("err9", file=sys.stderr)
          print("err10", file=sys.stderr)
        """.trimIndent(),
      )
    }

    val loggingProcess = withContext(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
      ExecService().executeGetProcess(
        binOnEel,
        Args(MAIN_PY),
        CoroutineScope(coroutineContext),
      ).getOrThrow()
    }

    // reading all stdout
    loggingProcess.inputStream.readAllBytes()

    waitUntil {
      service.loggedProcesses.value.lastOrNull()?.lines?.value?.size == 6
    }

    val process = service.loggedProcesses.value.last()

    // reading all stderr
    loggingProcess.errorStream.readAllBytes()

    waitUntil {
      service.loggedProcesses.value.lastOrNull()?.lines?.value?.size == 10
    }

    // copying output
    service.copyOutputToClipboard(process)

    // copied output should include tags
    assertEquals(
      """
        [stdout] out1
                 out2
                 out3
                 out4
                 out5
                 out6
        [stderr] err7
                 err8
                 err9
                 err10
          [exit] 0
        
      """.trimIndent(),
      CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor),
    )

    // toggling the show tags filter
    service.outputSectionState.filters[OutputFilter.Item.SHOW_TAGS] = false

    service.copyOutputToClipboard(process)

    // copied output should not include tags
    waitUntil("output without tags") {
      CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor) ==
        """
          out1
          out2
          out3
          out4
          out5
          out6
          err7
          err8
          err9
          err10
          0
          
      """.trimIndent()
    }
  }

  @Test
  fun `non-ascii output lines are reflected properly`(
    @TempDir cwd: Path,
    @PythonBinaryPath python: PythonBinary,
  ): Unit = timeoutRunBlocking {
    val service = projectFixture.get().service<ProcessOutputControllerService>()

    val binOnEel = BinOnEel(python, cwd)
    val mainPy = Files.createFile(cwd.resolve(MAIN_PY))
    val testTag = "non-ascii test"
    val nonAsciiText = "Привет, Мир"

    edtWriteAction {
      mainPy.toFile().writeText(
        """
          import sys 
          
          sys.stdout.buffer.write("$testTag\n".encode("utf8"))
          sys.stdout.buffer.write("$nonAsciiText\n".encode("utf8"))
        """.trimIndent(),
      )
    }

    runBin(binOnEel, Args(MAIN_PY))
    var lines: List<OutputLineDto>? = null

    waitUntil {
      service.loggedProcesses.value
        .lastOrNull()
        ?.lines
        ?.value
        ?.also {
          lines = it
        }
        ?.firstOrNull()
        ?.text == testTag
    }

    assertEquals(nonAsciiText, lines?.last()?.text)
  }

  @Test
  fun `tryOpenLogInToolWindow selects process, clears search, expands output, and returns true`(
    @TempDir cwd: Path,
    @PythonBinaryPath python: PythonBinary,
  ): Unit = timeoutRunBlocking {
    val service = projectFixture.get().service<ProcessOutputControllerService>()
    val binOnEel = BinOnEel(python, cwd)
    val mainPy = Files.createFile(cwd.resolve(MAIN_PY))
    val events = ConcurrentLinkedQueue<ProcessOutputController.Event>()

    val tryOpenLogInToolWindow =
      ProcessOutputControllerService::class.java
        .getDeclaredMethod("tryOpenLogInToolWindow", Int::class.javaPrimitiveType)
        .also { it.isAccessible = true }

    val eventCollectorJob = launch(Dispatchers.EDT) {
      service.events.collect { event ->
        events.add(event)
      }
    }

    try {
      edtWriteAction {
        mainPy.toFile().writeText(
          """
            print("hello")
          """.trimIndent(),
        )
      }

      val processDto = runBin(binOnEel, Args(MAIN_PY))
      val processId = processDto.id
      waitForProcess(service, processDto.id)

      // setting search
      service.search("initial search")

      // collapsing the output
      if (service.outputSectionState.isOutputExpanded.value) {
        service.toggleProcessOutput()
      }

      // search should be "initial search", output should not be expanded, no process selected, and no scroll event in the queue
      assertEquals("initial search", service.treeSectionState.searchQuery.value)
      assertEquals(false, service.outputSectionState.isOutputExpanded.value)
      assertEquals(null, service.selectedProcess.value)
      assertEquals(false, events.contains(ProcessOutputController.Event.OutputScrollDown))

      // try opening the error message in the tool window
      val result = tryOpenLogInToolWindow.invoke(service, processId) as Boolean

      // result should be true, correct process selected, search query empty and find an OutputScrollDown in the event queue
      assertEquals(true, result)
      waitUntil { service.selectedProcess.value?.data?.id == processId }
      waitUntil { service.treeSectionState.searchQuery.value.isEmpty() }
      waitUntil { service.outputSectionState.isOutputExpanded.value }
      events.waitForEvent { it is ProcessOutputController.Event.OutputScrollDown }

      // setting search
      service.search("still there")

      // collapsing process output again
      service.toggleProcessOutput()

      // deselecting process
      service.selectProcess(null)

      // search should be "still there", output should not be expanded, no process selected, and no scroll event in the queue
      assertEquals("still there", service.treeSectionState.searchQuery.value)
      assertEquals(false, service.outputSectionState.isOutputExpanded.value)
      assertEquals(null, service.selectedProcess.value)
      assertEquals(false, events.contains(ProcessOutputController.Event.OutputScrollDown))

      // try opening the error message in the tool window but non-existing process id
      val result2 = tryOpenLogInToolWindow.invoke(service, Int.MIN_VALUE) as Boolean

      // result should be false, no process selected, search query unchanged and no scroll event in the queue, and output not expanded
      assertEquals(false, result2)
      assertEquals("still there", service.treeSectionState.searchQuery.value)
      assertEquals(false, service.outputSectionState.isOutputExpanded.value)
      assertEquals(null, service.selectedProcess.value)
      assertEquals(false, events.contains(ProcessOutputController.Event.OutputScrollDown))
    }
    finally {
      eventCollectorJob.cancelAndJoin()
    }
  }

  @Test
  fun `line limits are maintained`(
    @TempDir cwd: Path,
    @PythonBinaryPath python: PythonBinary,
  ): Unit = timeoutRunBlocking {
    val service = projectFixture.get().service<ProcessOutputControllerService>()

    val binOnEel = BinOnEel(python, cwd)
    val mainPy = Files.createFile(cwd.resolve(MAIN_PY))

    edtWriteAction {
      mainPy.toFile().writeText(
        """
          import sys 
          
          for i in range(${ProcessOutputControllerServiceLimits.MAX_LINES} * 2):
            print("line " + str(i))
        """.trimIndent(),
      )
    }

    runBin(binOnEel, Args(MAIN_PY))

    var process: LoggedProcess? = null

    waitUntil {
      process = service.loggedProcesses.value.find {
        it.lines.value.getOrNull(0)?.text == "line ${ProcessOutputControllerServiceLimits.MAX_LINES}"
      }
      process != null
    }

    repeat(ProcessOutputControllerServiceLimits.MAX_LINES) {
      assertEquals(
        "line ${ProcessOutputControllerServiceLimits.MAX_LINES + it}",
        process!!.lines.value[it].text,
      )
    }
  }

  @Test
  fun `collectTopicEvents builds tree with nested contexts and root-level processes`(): Unit = timeoutRunBlocking {
    val service = projectFixture.get().service<ProcessOutputControllerService>()
    val parentContextUuid = TraceContextUuid("test-parent-context")
    val parentContext = TraceContextDto(
      title = "context X",
      timestamp = 1_000L,
      uuid = parentContextUuid,
      kind = TraceContextKind.INTERACTIVE,
      parentUuid = null,
    )
    val childContextUuid = TraceContextUuid("test-child-context")
    val childContext = TraceContextDto(
      title = "context Y",
      timestamp = 2_000L,
      uuid = childContextUuid,
      kind = TraceContextKind.INTERACTIVE,
      parentUuid = parentContext.uuid,
    )

    val testId = 900_000
    val process1 = createProcessDto(testId + 1, traceContext = null)
    val process2 = createProcessDto(testId + 2, traceContext = parentContext.uuid)
    val process3 = createProcessDto(testId + 3, traceContext = childContext.uuid)
    val process4 = createProcessDto(testId + 4, traceContext = parentContext.uuid)

    val allowedIds =
      setOf(
        ProcessTreeNode.Id.Context(parentContextUuid),
        ProcessTreeNode.Id.Context(childContextUuid),
        ProcessTreeNode.Id.Process(testId + 1),
        ProcessTreeNode.Id.Process(testId + 2),
        ProcessTreeNode.Id.Process(testId + 3),
        ProcessTreeNode.Id.Process(testId + 4),
      )

    // sending new process events
    sendProcessOutputTopicEvent(ProcessOutputEventDto.NewProcess(process1, emptyList()))
    sendProcessOutputTopicEvent(ProcessOutputEventDto.NewProcess(process2, listOf(parentContext)))
    sendProcessOutputTopicEvent(ProcessOutputEventDto.NewProcess(process3, listOf(parentContext, childContext)))
    sendProcessOutputTopicEvent(ProcessOutputEventDto.NewProcess(process4, listOf(parentContext)))

    // wait until all processes appear loggedProcesses
    waitUntil {
      val ids = service.loggedProcesses.value.map { it.data.id }.toSet()
      setOf(process1.id, process2.id, process3.id, process4.id).all { it in ids }
    }

    // wait until root has two expected entries: Context(parentContext) and Process(process1)
    lateinit var rootLevel: List<ProcessTreeNode>// = service.treeSectionState.treeRoot.value
    waitUntil {
      rootLevel = service.treeSectionState.treeRoot.value
      rootLevel.size >= 2
      && rootLevel.any { it is ProcessTreeNode.Context && it.uuid == parentContext.uuid }
      && rootLevel.any { it is ProcessTreeNode.Process && it.loggedProcess.data.id == process1.id }
    }

    val top = rootLevel.filter { it.id in allowedIds }

    // two root items: parentContext and process1
    assertEquals(2, top.size)
    val parentContextNode = top[0] as ProcessTreeNode.Context
    val process1Node = top[1] as ProcessTreeNode.Process
    assertEquals(parentContext.uuid, parentContextNode.uuid)
    assertEquals(process1.id, process1Node.loggedProcess.data.id)

    // parentContext's children: process4, childContext, process2
    val parentContextChildren = parentContextNode.children().toList().filterIsInstance<ProcessTreeNode>().filter { it.id in allowedIds }
    assertEquals(3, parentContextChildren.size)
    assertEquals(process4.id, (parentContextChildren[0] as ProcessTreeNode.Process).loggedProcess.data.id)
    val childContextNode = parentContextChildren[1] as ProcessTreeNode.Context
    assertEquals(childContext.uuid, childContextNode.uuid)
    assertEquals(process2.id, (parentContextChildren[2] as ProcessTreeNode.Process).loggedProcess.data.id)

    // childContext's children: process3
    val childContextChildren = childContextNode.children().toList().filterIsInstance<ProcessTreeNode>().filter { it.id in allowedIds }
    assertEquals(1, childContextChildren.size)
    assertEquals(process3.id, (childContextChildren[0] as ProcessTreeNode.Process).loggedProcess.data.id)
  }

  @Test
  fun `collectTopicEvents applies search query to tree`(): Unit = timeoutRunBlocking {
    val service = projectFixture.get().service<ProcessOutputControllerService>()

    val testId = 800_000
    val pythonProcess = createProcessDto(testId + 1, listOf("bin", "python"))
    val nodeProcess = createProcessDto(testId + 2, listOf("bin", "node"))
    val cargoProcess = createProcessDto(testId + 3, listOf("bin", "cargo"))

    val allowedIds =
      setOf(
        ProcessTreeNode.Id.Process(pythonProcess.id),
        ProcessTreeNode.Id.Process(nodeProcess.id),
        ProcessTreeNode.Id.Process(cargoProcess.id),
      )

    // sending new process events
    sendProcessOutputTopicEvent(ProcessOutputEventDto.NewProcess(pythonProcess, emptyList()))
    sendProcessOutputTopicEvent(ProcessOutputEventDto.NewProcess(nodeProcess, emptyList()))
    sendProcessOutputTopicEvent(ProcessOutputEventDto.NewProcess(cargoProcess, emptyList()))

    waitUntil {
      val ids = service.loggedProcesses.value.map { it.data.id }.toSet()
      setOf(pythonProcess.id, nodeProcess.id, cargoProcess.id).all { it in ids }
    }

    val processIdsInTree = {
      service.treeSectionState.treeRoot.value
        .filter { it.id in allowedIds }
        .filterIsInstance<ProcessTreeNode.Process>()
        .map { it.loggedProcess.data.id }
        .toSet()
    }

    // default empty search: all three processes are visible
    waitUntil {
      processIdsInTree() == setOf(pythonProcess.id, nodeProcess.id, cargoProcess.id)
    }

    // "python" matches only the python exe
    service.search("python")
    waitUntil { processIdsInTree() == setOf(pythonProcess.id) }

    // search is case-insensitive: "NODE" still matches the node exe
    service.search("NODE")
    waitUntil { processIdsInTree() == setOf(nodeProcess.id) }

    // substring match: "arg" is a substring of "cargo" only
    service.search("arg")
    waitUntil { processIdsInTree() == setOf(cargoProcess.id) }

    // tree is empty when no matches were found
    service.search("nothingatall")
    waitUntil { processIdsInTree().isEmpty() }

    // clearing the query brings every process back
    service.search("")
    waitUntil {
      processIdsInTree() == setOf(pythonProcess.id, nodeProcess.id, cargoProcess.id)
    }
  }

  @Test
  fun `collectTopicEvents applies SHOW_BACKGROUND_PROCESSES filter to tree`(): Unit = timeoutRunBlocking {
    val service = projectFixture.get().service<ProcessOutputControllerService>()

    val backgroundContextUuid = TraceContextUuid("test-background-context")
    val backgroundContext = TraceContextDto(
      title = "background context",
      timestamp = 1_000L,
      uuid = backgroundContextUuid,
      kind = TraceContextKind.NON_INTERACTIVE,
      parentUuid = null,
    )
    val interactiveContextUuid = TraceContextUuid("test-interactive-context")
    val interactiveContext = TraceContextDto(
      title = "interactive context",
      timestamp = 2_000L,
      uuid = interactiveContextUuid,
      kind = TraceContextKind.INTERACTIVE,
      parentUuid = null,
    )

    val testId = 700_000
    val backgroundProcess = createProcessDto(testId + 1, traceContext = backgroundContextUuid)
    val interactiveProcess = createProcessDto(testId + 2, traceContext = interactiveContextUuid)

    val allowedIds =
      setOf(
        ProcessTreeNode.Id.Context(interactiveContextUuid),
        ProcessTreeNode.Id.Process(backgroundProcess.id),
      )

    // sending new process events
    sendProcessOutputTopicEvent(ProcessOutputEventDto.NewProcess(backgroundProcess, listOf(backgroundContext)))
    sendProcessOutputTopicEvent(ProcessOutputEventDto.NewProcess(interactiveProcess, listOf(interactiveContext)))

    waitUntil {
      val ids = service.loggedProcesses.value.map { it.data.id }.toSet()
      setOf(backgroundProcess.id, interactiveProcess.id).all { it in ids }
    }

    val nodeIdsInTree = {
      service.treeSectionState.treeRoot.value
        .map { it.id }
        .filter { it in allowedIds }
        .toSet()
    }

    // by default, background processes are hidden
    waitUntil {
      nodeIdsInTree() == setOf(ProcessTreeNode.Id.Context(interactiveContextUuid))
    }

    // enabling the filter makes background processes visible
    service.treeSectionState.filters[TreeFilter.Item.SHOW_BACKGROUND_PROCESSES] = true
    waitUntil {
      nodeIdsInTree() == setOf(
        ProcessTreeNode.Id.Context(interactiveContextUuid),
        ProcessTreeNode.Id.Process(backgroundProcess.id),
      )
    }

    // disabling it again hides the background process
    service.treeSectionState.filters[TreeFilter.Item.SHOW_BACKGROUND_PROCESSES] = false
    waitUntil {
      nodeIdsInTree() == setOf(ProcessTreeNode.Id.Context(interactiveContextUuid))
    }
  }

  companion object {
    const val MAIN_PY = "main.py"

    fun List<OutputLineDto>.generatePreview(nLines: Int): String =
      buildString {
        for ((kind, text) in this@generatePreview.take(nLines)) {
          when (kind) {
            OutputKindDto.OUT -> append("OUT: ")
            OutputKindDto.ERR -> append("ERR: ")
          }

          appendLine(text)
        }
      }

    suspend fun runBinWithInput(binOnEel: BinOnEel, args: Args): Process =
      ExecService().executeGetProcess(
        binOnEel,
        args,
        CoroutineScope(NON_INTERACTIVE_ROOT_TRACE_CONTEXT),
      ).getOrThrow()

    suspend fun inputAndAwaitExit(process: Process) {
      process.outputStream.write(0)
      process.outputStream.flush()

      coroutineScope {
        listOf(
          async(Dispatchers.IO) {
            process.errorStream.readAllBytes()
          },
          async(Dispatchers.IO) {
            process.inputStream.readAllBytes()
          },
        ).awaitAll()

        process.awaitExit()
      }
    }

    suspend fun runBin(binOnEel: BinOnEel, args: Args): LoggedProcessDto =
      withContext(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
        val process = ExecService().executeGetProcess(
          binOnEel,
          args,
          CoroutineScope(coroutineContext),
        ).orThrow() as LoggingProcess

        coroutineScope {
          listOf(
            async(Dispatchers.IO) {
              process.errorStream.readAllBytes()
            },
            async(Dispatchers.IO) {
              process.inputStream.readAllBytes()
            },
          ).awaitAll()

          process.awaitExit()
        }

        process.loggedProcess
      }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun exitInfoCollectorCoroutinesCount(): Int =
      DebugProbes.dumpCoroutinesInfo()
        .filter { it.context[CoroutineName.Key]?.name == CoroutineNames.EXIT_INFO_COLLECTOR }
        .size

    private suspend fun waitForProcess(service: ProcessOutputControllerService, processId: Int): LoggedProcess {
      lateinit var process: LoggedProcess

      waitUntil {
        service.loggedProcesses.value.find { it.data.id == processId }?.also {
          process = it
        } != null
      }

      return process
    }

    private suspend fun ConcurrentLinkedQueue<ProcessOutputController.Event>.waitForEvent(
      callback: (ProcessOutputController.Event) -> Boolean,
    ): ProcessOutputController.Event {
      lateinit var event: ProcessOutputController.Event

      waitUntil {
        val polledEvent = poll() ?: return@waitUntil false

        return@waitUntil if (callback(polledEvent)) {
          event = polledEvent
          true
        }
        else {
          false
        }
      }

      return event
    }

    fun createProcessDto(
      id: Int,
      exeParts: List<String> = listOf("bin", "exe"),
      traceContext: TraceContextUuid? = null,
    ): LoggedProcessDto =
      LoggedProcessDto(
        weight = null,
        traceContextUuid = traceContext,
        pid = null,
        startedAt = Instant.fromEpochMilliseconds(0),
        cwd = null,
        exe = ExecutableDto(
          path = exeParts.joinToString("/"),
          parts = exeParts,
        ),
        args = emptyList(),
        env = emptyMap(),
        target = "",
        id = id,
      )
  }
}
