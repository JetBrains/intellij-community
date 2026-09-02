package com.intellij.python.processOutput.common

import com.intellij.openapi.components.Service
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.rpc.topics.ApplicationRemoteTopic
import com.intellij.platform.rpc.topics.ApplicationRemoteTopicListener
import com.intellij.platform.rpc.topics.sendToClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.UUID
import kotlin.time.Instant

@ApiStatus.Internal
@Serializable
enum class TraceContextKind {
  NON_INTERACTIVE,
  INTERACTIVE,
}

@ApiStatus.Internal
@Serializable
@JvmInline
value class TraceContextUuid(val uuid: String)

@ApiStatus.Internal
@Serializable
data class TraceContextDto(
  val title: @Nls String,
  val timestamp: Long,
  val uuid: TraceContextUuid,
  val kind: TraceContextKind,
  val parentUuid: TraceContextUuid?,
)

@ApiStatus.Internal
@Serializable
data class ExecutableDto(
  val path: String,
  val parts: List<String>,
)

@ApiStatus.Internal
@Serializable
enum class ProcessWeightDto {
  LIGHT,
  MEDIUM,
  HEAVY
}

@ApiStatus.Internal
@Serializable
data class LoggedProcessDto(
  val weight: ProcessWeightDto?,
  val traceContextUuid: TraceContextUuid?,
  val pid: Long?,
  val startedAt: Instant,
  val cwd: String?,
  val exe: ExecutableDto,
  val args: List<String>,
  val env: Map<String, String>,
  val target: String,
  val id: Int,
)

@ApiStatus.Internal
@Serializable
enum class OutputKindDto {
  OUT,
  ERR,
}

@ApiStatus.Internal
@Serializable
data class OutputLineDto(
  val kind: OutputKindDto,
  val text: String,
)

@ApiStatus.Internal
@Serializable
data class ExecErrorDto(
  val message: @NlsSafe String,
  val command: String,
  val reason: ExecErrorReasonDto,
  val loggedProcessId: Int? = null,
  val additionalMessageToUser: @NlsContexts.DialogTitle String? = null,
)

@ApiStatus.Internal
@Serializable
sealed interface ExecErrorReasonDto {
  @Serializable
  data class CantStart(val cantExecProcessError: String) : ExecErrorReasonDto

  @Serializable
  data class UnexpectedTermination(val stdout: String, val stderr: String, val exitCode: Int) : ExecErrorReasonDto

  @Serializable
  data object Timeout : ExecErrorReasonDto
}

@ApiStatus.Internal
@Serializable
sealed interface ProcessOutputEventDto {
  @Serializable
  data class NewProcess(val loggedProcess: LoggedProcessDto, val traceHierarchy: List<TraceContextDto>) : ProcessOutputEventDto

  @Serializable
  data class NewOutputLine(val processId: Int, val outputLine: OutputLineDto) : ProcessOutputEventDto

  @Serializable
  data class ProcessExit(val processId: Int, val exitedAt: Instant, val exitValue: Int) : ProcessOutputEventDto

  @Serializable
  data class ExecError(val execErrorDto: ExecErrorDto) : ProcessOutputEventDto

  @Serializable
  data class OpenToolWindowByTraceUuid(val uuid: TraceContextUuid, val openIfNotFound: Boolean) : ProcessOutputEventDto
}

private val PROCESS_OUTPUT_TOPIC: ApplicationRemoteTopic<ProcessOutputEventDto> =
  ApplicationRemoteTopic("PythonProcessOutputTopic", ProcessOutputEventDto.serializer())

@ApiStatus.Internal
fun sendNewProcessEvent(loggedProcessDto: LoggedProcessDto, traceHierarchy: List<TraceContextDto>) {
  PROCESS_OUTPUT_TOPIC.sendToClient(ProcessOutputEventDto.NewProcess(loggedProcessDto, traceHierarchy))
}

@ApiStatus.Internal
fun sendNewOutputLineEvent(processId: Int, outputLine: OutputLineDto) {
  PROCESS_OUTPUT_TOPIC.sendToClient(ProcessOutputEventDto.NewOutputLine(processId, outputLine))
}

@ApiStatus.Internal
fun sendProcessExitEvent(processId: Int, exitedAt: Instant, exitValue: Int) {
  PROCESS_OUTPUT_TOPIC.sendToClient(ProcessOutputEventDto.ProcessExit(processId, exitedAt, exitValue))
}

@ApiStatus.Internal
fun sendExecErrorEvent(execErrorDto: ExecErrorDto) {
  PROCESS_OUTPUT_TOPIC.sendToClient(ProcessOutputEventDto.ExecError(execErrorDto))
}

@ApiStatus.Internal
fun sendOpenToolWindowByTraceUuidEvent(uuid: UUID, openIfNotFound: Boolean = false) {
  sendOpenToolWindowByTraceUuidEvent(uuid.toString(), openIfNotFound)
}

@ApiStatus.Internal
fun sendOpenToolWindowByTraceUuidEvent(uuid: String, openIfNotFound: Boolean = false) {
  PROCESS_OUTPUT_TOPIC.sendToClient(
    ProcessOutputEventDto.OpenToolWindowByTraceUuid(
      TraceContextUuid(uuid),
      openIfNotFound,
    )
  )
}

internal class ProcessOutputTopicListener : ApplicationRemoteTopicListener<ProcessOutputEventDto> {
  override val topic: ApplicationRemoteTopic<ProcessOutputEventDto> = PROCESS_OUTPUT_TOPIC

  override fun handleEvent(event: ProcessOutputEventDto) {
    eventsChannel.trySend(event)
  }
}

private val eventsChannel = Channel<ProcessOutputEventDto>(capacity = UNLIMITED)

@ApiStatus.Internal
@Service
class FrontendTopicService(internal val coroutineScope: CoroutineScope) {
  val events: Flow<ProcessOutputEventDto> = eventsChannel.receiveAsFlow().shareIn(coroutineScope, SharingStarted.Eagerly)
}
