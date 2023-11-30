// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.statistics

import com.intellij.ide.impl.isTrusted
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.idea.svn.NestedCopyType
import org.jetbrains.idea.svn.SvnVcs
import java.util.*

private class SvnStatisticsCollector : ProjectUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(project: Project): Set<MetricEvent> {
    if (!project.isTrusted()) return emptySet()
    val vcs = SvnVcs.getInstance(project)
    if (vcs == null) return Collections.emptySet()
    // do not track roots with errors (SvnFileUrlMapping.getErrorRoots()) as they are "not usable" until errors are resolved
    // skip externals and switched directories as they will have the same format
    val roots = vcs.svnFileUrlMapping.allWcInfos.filter { it.type == null || it.type == NestedCopyType.inner }
    val workingCopyFormats = roots.map { it.format }.distinct()

    return workingCopyFormats
      .map { WORKING_COPY.metric(it.version.toCompactString()) }
      .toSet()
  }

  private val GROUP = EventLogGroup("svn.configuration", 2)
  private val WORKING_COPY = GROUP.registerEvent("working.copy",
                                                 EventFields.StringValidatedByRegexp("format", "version"))
}
