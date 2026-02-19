// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.InspectionProfile
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.jetbrains.python.inspections.PyCompatibilityInspection

private const val INSPECTION_SHORT_NAME = "PyCompatibilityInspection"

internal class PyCompatibilityInspectionCollector : ProjectUsagesCollector() {
  private val GROUP = EventLogGroup("python.inspection.compatibility", 1)
  private val PYTHON_VERSION = GROUP.registerEvent("python.versions", EventFields.StringListValidatedByRegexp("versions", "version"))

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val inspectionProfile: InspectionProfile = InspectionProjectProfileManager.getInstance(project).currentProfile
    if (!inspectionProfile.isToolEnabled(HighlightDisplayKey.find(INSPECTION_SHORT_NAME))) return emptySet()
    val compatInspection = inspectionProfile.getInspectionTool(INSPECTION_SHORT_NAME, project)?.tool as PyCompatibilityInspection?
    if (compatInspection == null) return emptySet()
    return setOf(PYTHON_VERSION.metric(compatInspection.ourVersions))
  }
}