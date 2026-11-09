package com.intellij.python.processOutput

import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.python.processOutput.common.ExecErrorDto
import com.intellij.python.processOutput.common.ExecErrorReasonDto
import com.intellij.python.processOutput.common.ExecutableDto
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.common.ProcessId
import com.intellij.python.processOutput.common.ProcessOutputTopic
import com.intellij.python.processOutput.frontend.LoggedProcess
import com.intellij.python.processOutput.frontend.ProcessTreeNode
import com.intellij.python.processOutput.frontend.childrenOf
import com.intellij.python.processOutput.frontend.ui.ProcessOutputUiContext
import com.intellij.python.processOutput.frontend.ui.UIEventListener
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * PY-91909 moved the exec-error dialogs to POTW's frontend: the sender emits one DTO carrying the error and
 * the associated process id, and the frontend decides where the error is shown.
 * `UIEventListener.displayExecError` routes to the tool window when the id resolves and this process has no
 * modal open, and to a modal dialog otherwise. Resolving the id is covered next door in
 * [ProcessOutputControllerImplTest]; this class locks the tool-window destination, with `selectedProcess` as
 * the discriminator because `selectProcess` is reached on that branch and nowhere else.
 *
 * **The other destination is not covered, and is not coverable here.** The modal branch ends in
 * `DialogWrapper.showAndGet()`, which throws on a non-modal dialog (`DialogWrapper.java:1886`) before
 * `doShow` consults `UiInterceptors`, and in a headless test application the dialog is never modal. The throw
 * also cancels this listener's own collector, so nothing after it is observable either. "An unresolved id
 * opens a dialog" and "an open modal keeps the error out of the tool window" therefore need a headed run.
 * They are left uncovered rather than faked: an assertion that only said "nothing was selected" would pass
 * just as happily against a listener that had died.
 */
@TestApplication
internal class UIEventListenerRoutingTest {
  private val projectFixture = projectFixture()
  private val disposableFixture = disposableFixture()

  @Test
  fun `an exec error whose process id resolves selects that process in the tool window`(): Unit =
    timeoutRunBlocking(TEST_TIMEOUT) {
      val context = startListener()
      val process = registerProcess(context, id = 701)

      ProcessOutputTopic.sendExecErrorEvent(execError(loggedProcessId = process.data.id))

      waitUntil("the errored process is selected") { context.controller.selectedProcess.value != null }
      assertEquals(process.data.id, context.controller.selectedProcess.value?.data?.id)
    }

  private suspend fun startListener(): ProcessOutputUiContext {
    val project = projectFixture.get()
    val context = ProcessOutputUiContext(project, JPanel(), disposableFixture.get())
    val toolWindow = ToolWindowManager.getInstance(project)
      .registerToolWindow(TOOL_WINDOW_ID, JPanel(), ToolWindowAnchor.BOTTOM)
    context.coroutineScope.launch { UIEventListener(context, toolWindow).listen(context.controller.uiEvents) }
    context.awaitTopicDelivery()
    return context
  }

  /**
   * Resends one throwaway process until it lands, because the controller subscribes to the topic from a plain
   * `launch` while the flow behind `FrontendTopicService.events` is `shareIn` with no replay: anything sent
   * before that collector attaches is dropped, and the collector exposes no readiness signal to wait on.
   * Once this returns the subscription exists, and every later event is sent exactly once.
   */
  private suspend fun ProcessOutputUiContext.awaitTopicDelivery() {
    waitUntil("the controller is collecting topic events") {
      if (controller.treeSectionState.treeRoot.value.processes().any { it.data.id.value == PROBE_ID }) return@waitUntil true
      ProcessOutputTopic.sendNewProcessEvent(processDto(PROBE_ID), emptyList())
      false
    }
  }

  /** Sends a `NewProcess` and returns once the controller's model carries it, so a later id can resolve. */
  private suspend fun registerProcess(context: ProcessOutputUiContext, id: Int): LoggedProcess {
    ProcessOutputTopic.sendNewProcessEvent(processDto(id), emptyList())
    lateinit var registered: LoggedProcess
    waitUntil("process $id reaches the model") {
      registered = context.controller.treeSectionState.treeRoot.value.processes().lastOrNull { it.data.id.value == id }
                   ?: return@waitUntil false
      true
    }
    return registered
  }

  private fun List<ProcessTreeNode>.processes(): List<LoggedProcess> = buildList {
    fun walk(nodes: List<ProcessTreeNode>) {
      for (node in nodes) when (node) {
        is ProcessTreeNode.Process -> add(node.loggedProcess)
        is ProcessTreeNode.Context -> walk(node.childrenOf<ProcessTreeNode>())
      }
    }
    walk(this@processes)
  }
}

private val TEST_TIMEOUT = 2.minutes
private const val PROBE_ID = 999_001
private const val TOOL_WINDOW_ID = "UIEventListenerRoutingTestToolWindow"

private fun execError(loggedProcessId: ProcessId?): ExecErrorDto = ExecErrorDto(
  message = "unexpected termination",
  command = "bin/exe",
  reason = ExecErrorReasonDto.UnexpectedTermination(stdout = "", stderr = "boom", exitCode = 1),
  loggedProcessId = loggedProcessId,
  additionalMessageToUser = "the command could not complete",
)

private fun processDto(id: Int): LoggedProcessDto = LoggedProcessDto(
  weight = null,
  traceContextUuid = null,
  pid = null,
  startedAt = Instant.fromEpochMilliseconds(id.toLong()),
  cwd = null,
  exe = ExecutableDto(path = "bin/exe", parts = listOf("bin", "exe")),
  args = emptyList(),
  env = emptyMap(),
  target = "",
  id = ProcessId(id),
)
