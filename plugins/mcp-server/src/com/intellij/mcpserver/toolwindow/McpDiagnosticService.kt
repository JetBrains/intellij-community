package com.intellij.mcpserver.toolwindow

import com.intellij.execution.services.ServiceEventListener
import com.intellij.mcpserver.ClientInfo
import com.intellij.mcpserver.McpCallInfo
import com.intellij.mcpserver.McpToolDescriptor
import com.intellij.mcpserver.McpToolSideEffectEvent
import com.intellij.mcpserver.ToolCallListener
import com.intellij.mcpserver.services.McpServiceViewContributor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@Service
internal class McpDiagnosticService(private val cs: CoroutineScope) {
  private val MAX_TOOL_CALLS = 5000

  private val _sessions = MutableStateFlow<List<McpSessionInfo>>(emptyList())
  private val _toolCalls = MutableStateFlow<List<McpToolCallEntry>>(emptyList())

  val activeSessionCount: Int get() = _sessions.value.size

  fun getSessions(): List<McpSessionInfo> = _sessions.value

  private fun createChildScope(name: String): CoroutineScope = cs.childScope(name)

  init {
    application.messageBus.connect().subscribe(ToolCallListener.TOPIC, object : ToolCallListener {
      override fun beforeMcpToolCall(mcpToolDescriptor: McpToolDescriptor, additionalData: McpCallInfo) {
        val entry = McpToolCallEntry(
          callId = additionalData.callId,
          sessionId = additionalData.sessionId,
          toolName = mcpToolDescriptor.name,
          clientInfo = additionalData.clientInfo,
          projectName = additionalData.project?.name,
          arguments = additionalData.rawArguments,
          startTimeMs = System.currentTimeMillis(),
          endTimeMs = null,
          status = ToolCallStatus.IN_PROGRESS,
          errorMessage = null,
          sideEffectsCount = 0,
        )
        _toolCalls.update { current ->
          val updated = current + entry
          if (updated.size > MAX_TOOL_CALLS) updated.drop(updated.size - MAX_TOOL_CALLS) else updated
        }
      }

      override fun afterMcpToolCall(
        mcpToolDescriptor: McpToolDescriptor,
        events: List<McpToolSideEffectEvent>,
        error: Throwable?,
        callInfo: McpCallInfo,
      ) {
        val endTime = System.currentTimeMillis()
        val status = when (error) {
          null -> ToolCallStatus.SUCCESS
          is CancellationException -> ToolCallStatus.CANCELLED
          else -> ToolCallStatus.ERROR
        }
        _toolCalls.update { current ->
          current.map { entry ->
            if (entry.callId == callInfo.callId) {
              entry.copy(
                endTimeMs = endTime,
                status = status,
                errorMessage = error?.message,
                sideEffectsCount = events.size,
              )
            }
            else entry
          }
        }
      }
    })
  }

  fun observeSessions(parentDisposable: Disposable, callback: (List<McpSessionInfo>) -> Unit) {
    val scope = createChildScope("MCP server diagnostic: observe sessions")
    Disposer.register(parentDisposable) {
      scope.cancel()
    }
    scope.launch {
      _sessions.collectLatest { sessions ->
        withContext(Dispatchers.EDT) { callback(sessions) }
      }
    }
  }

  fun observeToolCalls(parentDisposable: Disposable, callback: (List<McpToolCallEntry>) -> Unit) {
    val scope = createChildScope("MCP server diagnostic: observe tool calls")
    Disposer.register(parentDisposable) {
      scope.cancel()
    }
    scope.launch {
      _toolCalls.collectLatest { calls ->
        withContext(Dispatchers.EDT) { callback(calls) }
      }
    }
  }

  fun sessionStarted(sessionId: String, clientInfo: ClientInfo?, transportType: TransportType, startTimeMs: Long, localAgentId: String?) {
    val info = McpSessionInfo(
      sessionId = sessionId,
      clientInfo = clientInfo,
      transportType = transportType,
      startTimeMs = startTimeMs,
      localAgentId = localAgentId,
    )
    _sessions.update { it + info }
    fireServiceViewReset()
  }

  fun sessionEnded(sessionId: String) {
    _sessions.update { list ->
      list.filter { it.sessionId != sessionId }
    }
    fireServiceViewReset()
  }

  fun clearToolCalls() {
    _toolCalls.update { emptyList() }
  }

  private fun fireServiceViewReset() {
    application.messageBus
      .syncPublisher(ServiceEventListener.TOPIC)
      .handle(ServiceEventListener.ServiceEvent.createResetEvent(McpServiceViewContributor::class.java))
  }
}