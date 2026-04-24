package com.intellij.python.processOutput.common

import com.intellij.openapi.components.Service
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
sealed interface ProcessOutputEventDto {
  @Serializable
  data class NewProcess(val loggedProcess: LoggedProcessDto, val traceHierarchy: List<TraceContextDto>) : ProcessOutputEventDto

  @Serializable
  data class NewOutputLine(val processId: Int, val outputLine: OutputLineDto) : ProcessOutputEventDto

  @Serializable
  data class ProcessExit(val processId: Int, val exitedAt: Instant, val exitValue: Int) : ProcessOutputEventDto

  @Serializable
  class ReceivedQuery<TResponse : QueryResponsePayload> internal constructor(
    val query: ProcessOutputQuery<TResponse>,
  ) : ProcessOutputEventDto
}

@ApiStatus.Internal
val PROCESS_OUTPUT_TOPIC: ApplicationRemoteTopic<ProcessOutputEventDto> =
  ApplicationRemoteTopic("PythonProcessOutputTopic", ProcessOutputEventDto.serializer())

@ApiStatus.Internal
fun sendProcessOutputTopicEvent(event: ProcessOutputEventDto) {
  PROCESS_OUTPUT_TOPIC.sendToClient(event)
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
