// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.typeEngine

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

object PyTypeEngineUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("python.type.engine", 2)

  // Event fields
  private val TYPE_ENGINE = EventFields.Enum<PyTypeEngineType>("type_engine")

  // Events
  private val ENGINE_CHANGED = GROUP.registerVarargEvent(
    "engine.changed",
    TYPE_ENGINE,
  )

  private val SETTINGS_OPENED = GROUP.registerEvent("settings.opened")

  private val STATUS_BAR_WIDGET_CLICKED = GROUP.registerEvent("statusbar.widget.clicked")

  override fun getGroup(): EventLogGroup = GROUP

  fun logEngineChanged(
    project: Project?,
    newEngine: PyTypeEngineType,
  ) {
    ENGINE_CHANGED.log(
      project,
      TYPE_ENGINE.with(newEngine),
    )
  }

  fun logSettingsOpened(project: Project?) {
    SETTINGS_OPENED.log(project)
  }

  fun logStatusBarWidgetClicked(project: Project?) {
    STATUS_BAR_WIDGET_CLICKED.log(project)
  }
}
