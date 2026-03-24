package com.intellij.python.processOutput.common

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.set
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val QUERY_RESPONSE_TIMEOUT: Duration = 10.seconds

@ApiStatus.Internal
@Serializable
sealed interface QueryResponsePayload {
  @Serializable
  data object UnitPayload : QueryResponsePayload

  @Serializable
  data class BooleanPayload(val value: Boolean) : QueryResponsePayload
}

@ApiStatus.Internal
@Serializable
sealed interface QueryResponse<QueryResponsePayload> {
  @Serializable
  class Timeout<TResponse : QueryResponsePayload> : QueryResponse<TResponse>

  @Serializable
  data class Completed<TResponse : QueryResponsePayload>(val payload: TResponse) : QueryResponse<TResponse>
}

@ApiStatus.Internal
@Serializable
class QueryId internal constructor() {
  internal val id: Long = atomicId.getAndAdd(1)

  override fun equals(other: Any?): Boolean =
    when {
      this === other -> true
      other !is QueryId -> false
      else -> id == other.id
    }

  override fun hashCode(): Int {
    return id.hashCode()
  }

  companion object {
    private val atomicId = AtomicLong(0)
  }
}

@ApiStatus.Internal
@Serializable
sealed class ProcessOutputQuery<TResponse : QueryResponsePayload> {
  internal val id: QueryId = QueryId()

  suspend fun respond(response: TResponse) {
    BackEndApi().sendProcessOutputQueryResponse(id, response)
  }

  @Serializable
  data class SpecifyAdditionalMessageToUser(
    val processId: Int,
    val messageToUser: @Nls String,
  ) : ProcessOutputQuery<QueryResponsePayload.UnitPayload>()

  @Serializable
  data class OpenToolWindowWithError(
    val processId: Int,
  ) : ProcessOutputQuery<QueryResponsePayload.BooleanPayload>()
}

@ApiStatus.Internal
suspend fun <TResponse : QueryResponsePayload> sendProcessOutputQuery(
  query: ProcessOutputQuery<TResponse>,
): QueryResponse<TResponse> {
  val deferred = CompletableDeferred<TResponse>()
  val service = ApplicationManager.getApplication().service<QueryService>()

  @Suppress("UNCHECKED_CAST")
  service.queries[query.id] = deferred as CompletableDeferred<Any>

  sendProcessOutputTopicEvent(ProcessOutputEventDto.ReceivedQuery(query))

  val value =
    try {
      withTimeout(QUERY_RESPONSE_TIMEOUT) {
        QueryResponse.Completed(deferred.await())
      }
    }
    catch (_: TimeoutCancellationException) {
      QueryResponse.Timeout()
    }
    finally {
      service.queries.remove(query.id)
    }

  return value
}

@Service
private class QueryService {
  val queries = mutableMapOf<QueryId, CompletableDeferred<Any>>()
}

@ApiStatus.Internal
fun resolveProcessOutputQuery(requestId: QueryId, payload: QueryResponsePayload) {
  val service = ApplicationManager.getApplication().service<QueryService>()
  service.queries[requestId]?.complete(payload)
}

@ApiStatus.Internal
@Rpc
interface BackEndApi : RemoteApi<Unit> {
  suspend fun sendProcessOutputQueryResponse(requestId: QueryId, response: QueryResponsePayload)
}

@ApiStatus.Internal
suspend fun BackEndApi(): BackEndApi = RemoteApiProviderService.resolve(remoteApiDescriptor<BackEndApi>())
