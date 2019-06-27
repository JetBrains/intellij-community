// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.idea.svn.NestedCopyType
import org.jetbrains.idea.svn.SvnVcs

class SvnStatisticsCollector : ProjectUsagesCollector() {
  override fun getGroupId() = "svn.configuration"

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val vcs = SvnVcs.getInstance(project)

    // do not track roots with errors (SvnFileUrlMapping.getErrorRoots()) as they are "not usable" until errors are resolved
    // skip externals and switched directories as they will have the same format
    val roots = vcs.svnFileUrlMapping.allWcInfos.filter { it.type == null || it.type == NestedCopyType.inner }
    val workingCopyFormats = roots.map { it.format }.distinct()

    return workingCopyFormats
      .map { newMetric("working.copy", FeatureUsageData().addData("format", it.version.toCompactString())) }
      .toSet()
  }
}
