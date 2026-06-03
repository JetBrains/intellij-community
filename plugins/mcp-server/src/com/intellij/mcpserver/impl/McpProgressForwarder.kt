package com.intellij.mcpserver.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.activeTasks
import com.intellij.platform.ide.progress.updates
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.flow.throttle
import com.intellij.platform.util.progress.ProgressPipe
import com.intellij.platform.util.progress.ProgressState
import com.intellij.platform.util.progress.createProgressPipe
import fleet.kernel.rete.asValuesFlow
import fleet.kernel.tryWithEntities
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.ProgressToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val progressLogger = logger<McpProgressForwarder>()

private object McpProgressForwarder

private const val PROGRESS_NOTIFICATION_INTERVAL_REGISTRY_KEY = "mcp.server.progress.notification.interval.ms"

private val progressNotificationInterval: Duration
  get() = Registry.intValue(PROGRESS_NOTIFICATION_INTERVAL_REGISTRY_KEY, 1000).coerceAtLeast(1).milliseconds

private data class McpProgressEvent(
  val progress: Double,
  val total: Double?,
  val message: String?,
)

/**
 * Bridges IntelliJ coroutine-based progress API to MCP progress notifications,
 * executing [action] with progress updates forwarded to the MCP client.
 *
 * When [progressToken] is non-null, creates a [ProgressPipe] and forwards progress updates
 * (both inline and background project tasks) to the MCP client as `notifications/progress` messages.
 * When [progressToken] is null, simply executes [action].
 *
 * Tools report progress via standard IJ APIs (`reportRawProgress`, `reportProgress`,
 * `reportSequentialProgress`), and those updates are automatically forwarded.
 */
internal suspend fun <T> ServerSession.callToolWithProgressNotifications(
  project: Project?,
  progressToken: ProgressToken?,
  action: suspend CoroutineScope.() -> T,
): T = kotlinx.coroutines.coroutineScope {
  if (progressToken == null) {
    return@coroutineScope action()
  }

  val interval = progressNotificationInterval
  val progressPipe = createProgressPipe()
  val latestEvent = AtomicReference<McpProgressEvent?>(null)
  val lastSentEvent = AtomicReference<McpProgressEvent?>(null)
  val observeBackgroundTasks = AtomicBoolean(false)
  val sendingDisabled = AtomicBoolean(false)

  @Suppress("RAW_SCOPE_CREATION")
  val observerScope = CoroutineScope(currentCoroutineContext() + SupervisorJob())

  val progressFlow = merge(
    inlineProgressFlow(progressPipe),
    backgroundProgressFlow(project, observeBackgroundTasks),
  ).onEach { latestEvent.set(it) }

  observerScope.launch(start = CoroutineStart.UNDISPATCHED) {
    merge(
      progressFlow,
      heartbeatFlow(latestEvent, interval),
    )
      .throttle(interval.inWholeMilliseconds)
      .collect { event ->
        if (!sendProgressNotification(this@callToolWithProgressNotifications, progressToken, event, sendingDisabled)) {
          return@collect
        }
        lastSentEvent.set(event)
      }
  }

  try {
    yield()
    observeBackgroundTasks.set(true)
    progressPipe.collectProgressUpdates(action)
  }
  finally {
    observeBackgroundTasks.set(false)
    observerScope.cancel()
    flushLatestProgressNotification(
      session = this@callToolWithProgressNotifications,
      progressToken = progressToken,
      latestEvent = latestEvent.get(),
      lastSentEvent = lastSentEvent.get(),
      sendingDisabled = sendingDisabled,
    )
  }
}

private fun inlineProgressFlow(pipe: ProgressPipe): Flow<McpProgressEvent> = flow {
  var includeEmpty = true
  pipe.progressUpdates().collect { state ->
    normalizeProgressState(state, includeEmpty)?.let { emit(it) }
    includeEmpty = false
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun backgroundProgressFlow(project: Project?, observeBackgroundTasks: AtomicBoolean): Flow<McpProgressEvent> {
  if (project == null) {
    return emptyFlow()
  }

  val projectId = project.projectId()
  return flow {
    val seenTaskIds = HashSet<Any>()
    activeTasks.asValuesFlow().collect { task ->
      if (!observeBackgroundTasks.get()) return@collect
      if (task.projectEntity?.projectId != projectId) return@collect
      if (!seenTaskIds.add(task.eid)) return@collect
      emit(task)
    }
  }.flatMapMerge { task ->
    channelFlow {
      tryWithEntities(task) {
        task.updates.asValuesFlow()
          .mapNotNull { state -> normalizeProgressState(state, includeEmpty = false) }
          .collect { send(it) }
      }
    }
  }
}

private fun heartbeatFlow(
  latestEvent: AtomicReference<McpProgressEvent?>,
  interval: Duration,
): Flow<McpProgressEvent> = flow {
  while (currentCoroutineContext().isActive) {
    delay(interval)
    latestEvent.get()?.let { emit(it) }
  }
}

private fun normalizeProgressState(
  state: ProgressState,
  includeEmpty: Boolean,
): McpProgressEvent? {
  val fraction = state.fraction?.coerceIn(0.0, 1.0)
  val text = state.text
  val details = state.details
  val message = buildProgressMessage(text, details)
  if (!includeEmpty && fraction == null && message == null) {
    return null
  }
  return McpProgressEvent(
    progress = fraction ?: 0.0,
    total = if (fraction != null) 1.0 else null,
    message = message,
  )
}

private fun buildProgressMessage(text: String?, details: String?): String? {
  return when {
    text == null -> details
    details == null || details == text -> text
    else -> "$text: $details"
  }
}

private suspend fun flushLatestProgressNotification(
  session: ServerSession,
  progressToken: ProgressToken,
  latestEvent: McpProgressEvent?,
  lastSentEvent: McpProgressEvent?,
  sendingDisabled: AtomicBoolean,
) {
  if (latestEvent == null || latestEvent == lastSentEvent) {
    return
  }
  sendProgressNotification(session, progressToken, latestEvent, sendingDisabled)
}

private suspend fun sendProgressNotification(
  session: ServerSession,
  progressToken: ProgressToken,
  event: McpProgressEvent,
  sendingDisabled: AtomicBoolean,
): Boolean {
  if (sendingDisabled.get()) {
    return false
  }
  try {
    session.notification(
      ProgressNotification(
        ProgressNotificationParams(
          progressToken = progressToken,
          progress = event.progress,
          total = event.total,
          message = event.message,
        )
      )
    )
    return true
  }
  catch (ce: CancellationException) {
    throw ce
  }
  catch (t: Throwable) {
    sendingDisabled.set(true)
    progressLogger.warn("Failed to send MCP progress notification", t)
    return false
  }
}