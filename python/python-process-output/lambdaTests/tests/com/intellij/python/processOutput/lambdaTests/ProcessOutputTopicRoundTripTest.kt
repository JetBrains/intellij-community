// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.processOutput.lambdaTests

import com.intellij.ide.starter.driver.reused.ReusedProject
import com.intellij.ide.starter.driver.reused.ReusedRun
import com.intellij.idea.AppMode
import com.intellij.lambda.testFramework.junit.RunInMonolithAndSplitMode
import com.intellij.lambda.testFramework.junit.SetErrorIgnorerExtension
import com.intellij.lambda.testFramework.utils.IdeWithLambda
import com.intellij.openapi.components.service
import com.intellij.platform.rpc.topics.impl.RemoteTopicSubscribersManager
import com.intellij.python.processOutput.common.ExecErrorDto
import com.intellij.python.processOutput.common.ExecErrorReasonDto
import com.intellij.python.processOutput.common.ExecutableDto
import com.intellij.python.processOutput.common.FrontendTopicService
import com.intellij.python.processOutput.common.LoggedProcessDto
import com.intellij.python.processOutput.common.ProcessId
import com.intellij.python.processOutput.common.ProcessOutputEventDto
import com.intellij.python.processOutput.common.ProcessOutputTopic
import com.intellij.python.processOutput.common.ProcessWeightDto
import com.intellij.python.processOutput.common.TraceContextDto
import com.intellij.python.processOutput.common.TraceContextKind
import com.intellij.python.processOutput.common.TraceContextUuid
import com.intellij.python.processOutput.lambdaTests.util.PythonProcessOutputLambdaStrategy
import com.intellij.python.processOutput.lambdaTests.util.pythonProcessOutputLambdaConfiguration
import com.intellij.remoteDev.tests.impl.utils.waitSuspendingForOne
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * `PROCESS_OUTPUT_TOPIC` is POTW's only channel across the split boundary: the backend calls
 * `ProcessOutputTopic`, the frontend receives through `FrontendTopicService`. In monolith the platform hands the
 * listener the original object and nothing is serialized; in split the payload goes through the topic's kotlinx
 * serializer. This class is the lock on that wire format, and the split invocation is the only place a field
 * that stopped surviving the trip would show up.
 *
 * One case per event shape that has its own generated serializer, because that is the equivalence class here —
 * a `NewProcess` round trip cannot observe a field of `ExecErrorDto`. The revert that proves any of them:
 * mark a field `@Transient` and the case goes green in monolith and red in split.
 *
 * `ExecErrorDto.loggedProcessId` is the field PY-91909 introduced so the frontend can route an error without
 * querying back. What the frontend *does* when that id resolves to nothing is a routing decision, not a wire
 * claim, and lives at unit level in `ProcessOutputControllerImplTest`.
 */
@RunInMonolithAndSplitMode
@ExtendWith(SetErrorIgnorerExtension::class)
@ReusedRun(sessionStrategy = PythonProcessOutputLambdaStrategy::class)
internal class ProcessOutputTopicRoundTripTest {

  companion object {
    @ReusedProject
    private val project = pythonProcessOutputLambdaConfiguration
  }

  @Test
  fun `a new-process event keeps every field when it reaches the frontend`(ide: IdeWithLambda) =
    timeoutRunBlocking(TEST_TIMEOUT) {
      ide {
        subscribeToTopic(ProcessOutputEventDto.NewProcess::class.java)
        runInBackend("Send a NewProcess event once the frontend is a subscriber") {
          awaitFrontendSubscriber()
          ProcessOutputTopic.sendNewProcessEvent(sentProcess(), listOf(sentTraceContext()))
        }
        assertThat(readArrivedEvent())
          .describedAs("the NewProcess event as the frontend received it")
          .isEqualTo(render(ProcessOutputEventDto.NewProcess(sentProcess(), listOf(sentTraceContext()))))
      }
    }

  @Test
  fun `an exec error keeps the associated process id and every other field when it reaches the frontend`(ide: IdeWithLambda) =
    timeoutRunBlocking(TEST_TIMEOUT) {
      ide {
        subscribeToTopic(ProcessOutputEventDto.ExecError::class.java)
        // One call, one DTO — the point of the ticket: no query back to learn which process the error belongs to.
        runInBackend("Send an ExecError event once the frontend is a subscriber") {
          awaitFrontendSubscriber()
          ProcessOutputTopic.sendExecErrorEvent(sentTerminationError())
        }
        assertThat(readArrivedEvent())
          .describedAs("the ExecError event as the frontend received it, including the associated process id")
          .isEqualTo(render(ProcessOutputEventDto.ExecError(sentTerminationError())))
      }
    }

  /** `Timeout` is a `data object`, so it serializes through a different path than the other two reasons. */
  @Test
  fun `an exec error with a data-object reason and no associated process keeps both`(ide: IdeWithLambda) =
    timeoutRunBlocking(TEST_TIMEOUT) {
      ide {
        subscribeToTopic(ProcessOutputEventDto.ExecError::class.java)
        runInBackend("Send a timed-out ExecError event once the frontend is a subscriber") {
          awaitFrontendSubscriber()
          ProcessOutputTopic.sendExecErrorEvent(sentTimeoutError())
        }
        assertThat(readArrivedEvent())
          .describedAs("the timed-out ExecError event as the frontend received it")
          .isEqualTo(render(ProcessOutputEventDto.ExecError(sentTimeoutError())))
      }
    }
}

private val TEST_TIMEOUT = 5.minutes
private val STEP_TIMEOUT = 30.seconds

private const val TRACE_UUID = "6f1f1f2e-0000-4000-8000-000000000001"

/**
 * Starts collecting on the frontend and returns once the subscription exists, not once the collector is
 * launched. A replay-0 `SharedFlow` drops an emit that finds no subscriber, so a send ordered before this
 * returns is lost; `onSubscription` is what makes the ordering observable.
 */
private suspend fun IdeWithLambda.subscribeToTopic(eventClass: Class<out ProcessOutputEventDto>) {
  runInFrontend("Subscribe to the process output topic", parameters = listOf(eventClass)) { params ->
    val expected = params.single() as Class<*>
    @Suppress("UNCHECKED_CAST")
    val events = service<FrontendTopicService>().events as SharedFlow<ProcessOutputEventDto>
    val received = CompletableDeferred<ProcessOutputEventDto>()
    val subscribed = CompletableDeferred<Unit>()
    testData = received

    launch {
      received.complete(
        events
          .onSubscription { subscribed.complete(Unit) }
          .filter { expected.isInstance(it) }
          .first()
      )
    }
    withTimeout(STEP_TIMEOUT) { subscribed.await() }
  }
}

/**
 * Blocks until the frontend is a client the backend will actually deliver to.
 *
 * `sendToClient` looks its target up in `RemoteTopicSubscribersManager` and does nothing at all when that client
 * is not registered yet — silently, and by design: "if the client is not yet connected, but an event is sent, the
 * event won't be received". The `onSubscription` barrier in [subscribeToTopic] sits one level above this. It says
 * when the test's collector is attached to `FrontendTopicService`; it says nothing about whether the frontend's
 * own `FrontendRemoteTopicListenersRegistry` has reached the backend over RPC. Without this second barrier the
 * split invocation fails as a 30-second read timeout with no error on either side, and how often depends on how
 * fast the frontend starts.
 *
 * Monolith returns early: `connectedRemoteClients` filters the local client out, so there is never one to wait
 * for, and there is no race either — the local client is registered while the manager is being constructed.
 */
private suspend fun awaitFrontendSubscriber() {
  if (!AppMode.isRemoteDevHost()) return
  waitSuspendingForOne("The frontend is a registered remote-topic subscriber", STEP_TIMEOUT, getter = {
    RemoteTopicSubscribersManager.getInstance().connectedRemoteClients()
  })
}

/** Reads what the collector caught. Only a `java.io.Serializable` can come back and the DTOs are kotlinx-only, hence the rendering. */
private suspend fun IdeWithLambda.readArrivedEvent(): String =
  runInFrontendGetResult("Read what arrived on the frontend") {
    @Suppress("UNCHECKED_CAST")
    val received = testData as CompletableDeferred<ProcessOutputEventDto>
    render(withTimeout(STEP_TIMEOUT) { received.await() })
  } as String

/** Built identically on both sides, so the comparison is against the values sent, not against a snapshot. */
private fun sentProcess(): LoggedProcessDto = LoggedProcessDto(
  weight = ProcessWeightDto.MEDIUM,
  traceContextUuid = TraceContextUuid(TRACE_UUID),
  pid = 4242L,
  startedAt = Instant.fromEpochMilliseconds(1_700_000_000_123L),
  cwd = "/tmp/потw work dir",
  exe = ExecutableDto(path = "/usr/bin/python3", parts = listOf("usr", "bin", "python3")),
  args = listOf("-c", "print('привет')"),
  env = mapOf("POTW_TEST" to "значение", "POTW_EMPTY" to ""),
  target = "local",
  id = ProcessId(4242),
)

private fun sentTraceContext(): TraceContextDto = TraceContextDto(
  title = "Установка пакета",
  timestamp = 1_700_000_000_000L,
  uuid = TraceContextUuid(TRACE_UUID),
  kind = TraceContextKind.NON_INTERACTIVE,
  parentUuid = null,
)

private fun sentTerminationError(): ExecErrorDto = ExecErrorDto(
  message = "Не удалось запустить процесс",
  command = "/usr/bin/python3 -c \"1/0\"",
  reason = ExecErrorReasonDto.UnexpectedTermination(
    stdout = "частичный вывод",
    stderr = "ZeroDivisionError: division by zero",
    exitCode = 1,
  ),
  loggedProcessId = ProcessId(777),
  additionalMessageToUser = "Не удалось выполнить команду",
)

private fun sentTimeoutError(): ExecErrorDto = ExecErrorDto(
  message = "Процесс не ответил",
  command = "/usr/bin/python3 -m pip install jinja",
  reason = ExecErrorReasonDto.Timeout,
  loggedProcessId = null,
  additionalMessageToUser = null,
)

/**
 * One renderer over the sealed event type, so a new case is a branch here rather than a second class. A
 * canonical string rather than comparing DTOs: only `java.io.Serializable` values come back to the test
 * process, and `ProcessOutputEventDto` is kotlinx-`@Serializable`, which is a different mechanism.
 */
private fun render(event: ProcessOutputEventDto): String = when (event) {
  is ProcessOutputEventDto.NewProcess -> buildString {
    val p = event.loggedProcess
    appendLine("NewProcess")
    appendLine("id=${p.id.value}")
    appendLine("pid=${p.pid}")
    appendLine("weight=${p.weight}")
    appendLine("startedAtMillis=${p.startedAt.toEpochMilliseconds()}")
    appendLine("cwd=${p.cwd}")
    appendLine("exePath=${p.exe.path}")
    appendLine("exeParts=${p.exe.parts.joinToString("|")}")
    appendLine("args=${p.args.joinToString("|")}")
    appendLine("env=${p.env.entries.sortedBy { it.key }.joinToString("|") { "${it.key}=${it.value}" }}")
    appendLine("target=${p.target}")
    appendLine("traceContextUuid=${p.traceContextUuid?.value}")
    appendLine("hierarchy=${event.traceHierarchy.joinToString("|") { "${it.uuid.value}:${it.kind}:${it.title}" }}")
  }
  is ProcessOutputEventDto.ExecError -> buildString {
    val e = event.execErrorDto
    appendLine("ExecError")
    appendLine("message=${e.message}")
    appendLine("command=${e.command}")
    appendLine("loggedProcessId=${e.loggedProcessId?.value}")
    appendLine("additionalMessageToUser=${e.additionalMessageToUser}")
    appendLine(
      when (val reason = e.reason) {
        is ExecErrorReasonDto.CantStart -> "reason=CantStart(${reason.cantExecProcessError})"
        is ExecErrorReasonDto.UnexpectedTermination ->
          "reason=UnexpectedTermination(stdout=${reason.stdout}, stderr=${reason.stderr}, exitCode=${reason.exitCode})"
        ExecErrorReasonDto.Timeout -> "reason=Timeout"
      }
    )
  }
  is ProcessOutputEventDto.NewOutputLine -> "NewOutputLine ${event.processId.value} ${event.outputLine.kind} ${event.outputLine.text}"
  is ProcessOutputEventDto.ProcessExit -> "ProcessExit ${event.processId.value} ${event.exitValue}"
  is ProcessOutputEventDto.OpenToolWindowByTraceUuid -> "OpenToolWindowByTraceUuid ${event.uuid.value} ${event.openIfNotFound}"
}
