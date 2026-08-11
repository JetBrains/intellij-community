// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.demo

import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.webview.api.WebViewMessageBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns the demo board state on the Kotlin side. Populates a 168-task seed on [start],
 * ticks every 2 seconds, and applies mutations requested by the WebView via [WebViewMessageBus].
 * Every state change ends with a full snapshot published back to JS.
 */
internal class DemoBoardProducer(
  private val scope: CoroutineScope,
  private val bus: WebViewMessageBus,
) {
  private val mutex = Mutex()
  private val tasks: MutableList<SampleTask> = mutableListOf()
  private val timeline: MutableList<SampleTimelineEntry> = mutableListOf()
  private val nextTaskNum = AtomicInteger(1000 + SEED_SIZE)
  private val nextTimelineNum = AtomicInteger(SEED_TIMELINE_SIZE + 1)
  private val nextMinutes = AtomicInteger(1)
  private val snapshotPublishCount = AtomicInteger()

  @Volatile
  private var autoTickEnabled: Boolean = true

  fun start() {
    LOG.info("starting demo board producer")
    scope.launch {
      mutex.withLock { seedInitial() }
      publishSnapshot(SNAPSHOT_REASON_INITIAL)
      registerSubscriptions()
      runTicker()
    }
  }

  private fun registerSubscriptions() {
    bus.registerNotificationHandler(DemoBoardNotifications.ready) { _, _ ->
      LOG.info("received ready from WebView; publishing current board snapshot")
      publishSnapshot(SNAPSHOT_REASON_READY)
    }
    bus.registerNotificationHandler(DemoBoardNotifications.taskClicked) { event, _ ->
      LOG.info("task clicked: ${event.taskId}")
    }
    bus.registerNotificationHandler(DemoBoardNotifications.advance) { event, _ ->
      applyAndPublish(SNAPSHOT_REASON_MUTATION) { mutateAdvance(event.taskId) }
    }
    bus.registerNotificationHandler(DemoBoardNotifications.toggleBlocked) { event, _ ->
      applyAndPublish(SNAPSHOT_REASON_MUTATION) { mutateToggleBlocked(event.taskId) }
    }
    bus.registerNotificationHandler(DemoBoardNotifications.moveStatus) { event, _ ->
      applyAndPublish(SNAPSHOT_REASON_MUTATION) { mutateMoveStatus(event.taskId, event.direction) }
    }
    bus.registerNotificationHandler(DemoBoardNotifications.setStatus) { event, _ ->
      applyAndPublish(SNAPSHOT_REASON_MUTATION) { mutateSetStatus(event.taskId, event.status) }
    }
    bus.registerNotificationHandler(DemoBoardNotifications.setAutoTick) { event, _ ->
      if (autoTickEnabled == event.enabled) return@registerNotificationHandler
      autoTickEnabled = event.enabled
      LOG.info("auto-tick -> ${event.enabled}")
      publishSnapshot(SNAPSHOT_REASON_AUTO_TICK)
    }
    bus.registerNotificationHandler(DemoBoardNotifications.addTask) { _, _ ->
      applyAndPublish(SNAPSHOT_REASON_MUTATION) { mutateAddTask() }
    }
    bus.registerNotificationHandler(DemoBoardNotifications.deleteTask) { event, _ ->
      applyAndPublish(SNAPSHOT_REASON_MUTATION) { mutateDelete(event.taskId) }
    }
  }

  private suspend fun runTicker() {
    while (scope.isActive) {
      delay(TICK_MS.milliseconds)
      if (!autoTickEnabled) continue
      applyAndPublish(SNAPSHOT_REASON_TICK) { mutateTick() }
    }
  }

  private suspend fun applyAndPublish(reason: String, mutation: () -> Unit) {
    mutex.withLock { mutation() }
    publishSnapshot(reason)
  }

  private suspend fun publishSnapshot(reason: String) {
    val snapshot = mutex.withLock {
      SampleBoardSnapshot(
        tasks = tasks.toList(),
        timeline = timeline.toList(),
        updatedAt = System.currentTimeMillis(),
        autoTick = autoTickEnabled,
        roadmapMermaid = SampleRoadmapMermaid.build(tasks),
      )
    }
    logSnapshot(reason, snapshot)
    bus.notify(DemoBoardNotifications.snapshot, snapshot)
  }

  private fun logSnapshot(reason: String, snapshot: SampleBoardSnapshot) {
    val count = snapshotPublishCount.incrementAndGet()
    if ((reason == SNAPSHOT_REASON_MUTATION || reason == SNAPSHOT_REASON_TICK) && count > SNAPSHOT_INFO_LIMIT) {
      LOG.debug("publishing board snapshot #$count reason=$reason tasks=${snapshot.tasks.size} timeline=${snapshot.timeline.size}")
      return
    }
    LOG.info(
      "publishing board snapshot #$count reason=$reason tasks=${snapshot.tasks.size} " +
      "timeline=${snapshot.timeline.size} autoTick=${snapshot.autoTick}",
    )
  }

  private fun seedInitial() {
    val statuses = SampleBoardConstants.STATUSES
    val priorities = SampleBoardConstants.PRIORITIES
    val owners = SampleBoardConstants.OWNERS
    val domains = SampleBoardConstants.DOMAINS
    for (index in 0 until SEED_SIZE) {
      val status = statuses[index % statuses.size]
      val priority = priorities[(index * 7) % priorities.size]
      val blocked = index % 11 == 0
      val owner = owners[index % owners.size]
      val domain = domains[index % domains.size]
      val progressBase = when (status) {
        "Done" -> 100
        "Review" -> 76
        "In Progress" -> 45
        else -> 14
      }
      val progress = (progressBase + (index % 18)).coerceAtMost(100)
      val dueOffset = (index % 15) - 4
      tasks += SampleTask(
        id = "WVW-${1000 + index}",
        title = "$domain rich interaction scenario #${index + 1}",
        owner = owner,
        status = status,
        priority = priority,
        blocked = blocked,
        progress = progress,
        dueOffset = dueOffset,
        dueLabel = dayLabel(dueOffset),
        estimateHours = 2 + (index % 8),
        tags = listOf(
          domain,
          if (index % 2 == 0) "UI" else "Bridge",
          if (index % 3 == 0) "Regression" else "Smoke",
        ),
      )
    }
    for (index in 0 until SEED_TIMELINE_SIZE) {
      val task = tasks[index % tasks.size]
      val action = ACTIONS[index % ACTIONS.size]
      val minutesAgo = 3 + index * 2
      timeline += SampleTimelineEntry(
        id = "EVT-${index + 1}",
        taskId = task.id,
        label = "${task.id} \u2022 $action \u2022 ${minutesAgo}m ago",
      )
    }
  }

  private fun mutateTick() {
    if (tasks.isEmpty()) return
    val pickIdx = (Math.random() * tasks.size).toInt().coerceAtMost(tasks.size - 1)
    val task = tasks[pickIdx]
    val bumped = (task.progress + 5).coerceAtMost(100)
    val newStatus = if (bumped >= 100) "Done" else rotateStatus(task.status, forward = true, onDoneStay = true)
    tasks[pickIdx] = task.copy(progress = bumped, status = newStatus)
    appendTimeline(task.id, "progress ${task.progress}\u2192$bumped")
  }

  private fun mutateAdvance(taskId: String) {
    val idx = tasks.indexOfFirst { it.id == taskId }
    if (idx < 0) return
    val task = tasks[idx]
    tasks[idx] = task.copy(progress = (task.progress + 10).coerceAtMost(100))
    appendTimeline(taskId, "advance +10%")
  }

  private fun mutateToggleBlocked(taskId: String) {
    val idx = tasks.indexOfFirst { it.id == taskId }
    if (idx < 0) return
    val task = tasks[idx]
    tasks[idx] = task.copy(blocked = !task.blocked)
    appendTimeline(taskId, if (!task.blocked) "blocked" else "unblocked")
  }

  private fun mutateMoveStatus(taskId: String, direction: String) {
    val idx = tasks.indexOfFirst { it.id == taskId }
    if (idx < 0) return
    val task = tasks[idx]
    val forward = direction != "prev"
    val newStatus = rotateStatus(task.status, forward = forward, onDoneStay = false)
    tasks[idx] = task.copy(status = newStatus)
    appendTimeline(taskId, "status \u2192 $newStatus")
  }

  private fun mutateSetStatus(taskId: String, status: String) {
    if (status !in SampleBoardConstants.STATUSES) {
      LOG.warn("setStatus: rejecting unknown status \"$status\" for $taskId")
      return
    }
    val idx = tasks.indexOfFirst { it.id == taskId }
    if (idx < 0) return
    val task = tasks[idx]
    if (task.status == status) return
    tasks[idx] = task.copy(status = status)
    appendTimeline(taskId, "drag \u2192 $status")
  }

  private fun mutateAddTask() {
    val index = nextTaskNum.getAndIncrement()
    val domain = SampleBoardConstants.DOMAINS.random()
    val owner = SampleBoardConstants.OWNERS.random()
    tasks.add(
      0,
      SampleTask(
        id = "WVW-$index",
        title = "$domain new request ${index - 1000 + 1}",
        owner = owner,
        status = "Backlog",
        priority = "Medium",
        blocked = false,
        progress = 0,
        dueOffset = 3,
        dueLabel = dayLabel(3),
        estimateHours = 4,
        tags = listOf(domain, "UI", "Smoke"),
      ),
    )
    appendTimeline("WVW-$index", "added by UI")
  }

  private fun mutateDelete(taskId: String) {
    val removed = tasks.removeAll { it.id == taskId }
    if (removed) appendTimeline(taskId, "deleted by UI")
  }

  private fun appendTimeline(taskId: String, action: String) {
    val evtId = "EVT-${nextTimelineNum.getAndIncrement()}"
    val minutesAgo = nextMinutes.getAndIncrement()
    timeline.add(
      0,
      SampleTimelineEntry(id = evtId, taskId = taskId, label = "$taskId \u2022 $action \u2022 ${minutesAgo}m ago"),
    )
  }

  private fun rotateStatus(current: String, forward: Boolean, onDoneStay: Boolean): String {
    val statuses = SampleBoardConstants.STATUSES
    val idx = statuses.indexOf(current).coerceAtLeast(0)
    val next = if (forward) (idx + 1).coerceAtMost(statuses.size - 1) else (idx - 1).coerceAtLeast(0)
    if (onDoneStay && statuses[next] == "Done") return "Done"
    return statuses[next]
  }

  private fun dayLabel(offsetDays: Int): String = LocalDate.now().plusDays(offsetDays.toLong()).format(DATE_FORMAT)

  private companion object {
    private val LOG = logger<DemoBoardProducer>()
    private const val SEED_SIZE = 168
    private const val SEED_TIMELINE_SIZE = 320
    private const val TICK_MS = 2_000L
    private const val SNAPSHOT_INFO_LIMIT = 5
    private const val SNAPSHOT_REASON_INITIAL = "initial"
    private const val SNAPSHOT_REASON_READY = "ready"
    private const val SNAPSHOT_REASON_MUTATION = "mutation"
    private const val SNAPSHOT_REASON_TICK = "tick"
    private const val SNAPSHOT_REASON_AUTO_TICK = "autoTick"
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    private val ACTIONS = listOf("status changed", "comment added", "review requested", "build linked", "QA note")
  }
}
