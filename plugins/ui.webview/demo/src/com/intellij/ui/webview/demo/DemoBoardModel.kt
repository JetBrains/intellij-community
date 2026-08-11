// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.demo

import com.intellij.ui.webview.api.WebViewNotification
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Board snapshot published Kotlin → JS.
 */
@Serializable
internal data class SampleBoardSnapshot(
  val tasks: List<SampleTask>,
  val timeline: List<SampleTimelineEntry>,
  val updatedAt: Long,
  /** Whether the Kotlin producer's 2s auto-tick is currently running. */
  val autoTick: Boolean = true,
  /**
   * Mermaid source string for the "product roadmap" panel. Rendered by the
   * WebView with the bundled mermaid script; regenerated per snapshot so
   * live board numbers can appear inside the diagram.
   */
  val roadmapMermaid: String = "",
)

@Serializable
internal data class SampleTask(
  val id: String,
  val title: String,
  val owner: String,
  val status: String,
  val priority: String,
  val blocked: Boolean,
  val progress: Int,
  val dueOffset: Int,
  val dueLabel: String,
  val estimateHours: Int,
  val tags: List<String>,
)

@Serializable
internal data class SampleTimelineEntry(
  val id: String,
  val taskId: String,
  val label: String,
)

/** Event payloads shipped JS → Kotlin. */
@Serializable
internal data class TaskIdEvent(val taskId: String)

@Serializable
internal data class MoveStatusEvent(val taskId: String, val direction: String)

@Serializable
internal data class SetStatusEvent(val taskId: String, val status: String)

@Serializable
internal data class SetAutoTickEvent(val enabled: Boolean)

@Serializable
internal class EmptyDemoEvent

internal class DemoBoardNotification<Params : Any>(
  override val method: String,
  override val paramsSerializer: KSerializer<Params>,
) : WebViewNotification<Params>

internal object DemoBoardMethods {
  const val SNAPSHOT: String = "demo/board/snapshot"
  const val READY: String = "demo/board/ready"
  const val TASK_CLICKED: String = "demo/board/taskClicked"
  const val ADVANCE: String = "demo/board/advance"
  const val TOGGLE_BLOCKED: String = "demo/board/toggleBlocked"
  const val MOVE_STATUS: String = "demo/board/moveStatus"
  const val SET_STATUS: String = "demo/board/setStatus"
  const val SET_AUTO_TICK: String = "demo/board/setAutoTick"
  const val ADD_TASK: String = "demo/board/addTask"
  const val DELETE_TASK: String = "demo/board/deleteTask"
}

internal object DemoBoardNotifications {
  val snapshot = DemoBoardNotification(DemoBoardMethods.SNAPSHOT, SampleBoardSnapshot.serializer())
  val ready = DemoBoardNotification(DemoBoardMethods.READY, EmptyDemoEvent.serializer())
  val taskClicked = DemoBoardNotification(DemoBoardMethods.TASK_CLICKED, TaskIdEvent.serializer())
  val advance = DemoBoardNotification(DemoBoardMethods.ADVANCE, TaskIdEvent.serializer())
  val toggleBlocked = DemoBoardNotification(DemoBoardMethods.TOGGLE_BLOCKED, TaskIdEvent.serializer())
  val moveStatus = DemoBoardNotification(DemoBoardMethods.MOVE_STATUS, MoveStatusEvent.serializer())
  val setStatus = DemoBoardNotification(DemoBoardMethods.SET_STATUS, SetStatusEvent.serializer())
  val setAutoTick = DemoBoardNotification(DemoBoardMethods.SET_AUTO_TICK, SetAutoTickEvent.serializer())
  val addTask = DemoBoardNotification(DemoBoardMethods.ADD_TASK, EmptyDemoEvent.serializer())
  val deleteTask = DemoBoardNotification(DemoBoardMethods.DELETE_TASK, TaskIdEvent.serializer())
}

internal object SampleBoardConstants {
  val STATUSES: List<String> = listOf("Backlog", "In Progress", "Review", "Done")
  val PRIORITIES: List<String> = listOf("Low", "Medium", "High", "Critical")
  val OWNERS: List<String> = listOf("Alice", "Bob", "Carol", "Diana", "Evan", "Fatima", "George", "Helen")
  val DOMAINS: List<String> = listOf("WebView", "Editor", "Terminal", "Search", "Navigation", "Inspections")
}
