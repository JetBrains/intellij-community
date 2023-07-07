// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenProjectsManager

class MavenPluginCollector : ProjectUsagesCollector() {

  private val groupAndArtifactId = EventFields.StringValidatedByCustomRule<MavenIdValidationRule>("groupAndArtifactId")
  private val version = EventFields.StringValidatedByCustomRule<MavenIdValidationRule>("version")
  private val isExtension = EventFields.Boolean("extension")

  private val mavenPluginId = group.registerVarargEvent("mavenPluginId",
                                                        groupAndArtifactId, version)

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val manager = MavenProjectsManager.getInstance(project)
    if (!manager.isMavenizedProject) return emptySet()
    return manager.projects
      .flatMap { it.declaredPlugins }
      .map {
        mavenPluginId.metric(
          groupAndArtifactId with "${it.groupId}:${it.artifactId}",
          version with it.version,
          isExtension with it.isExtensions
        )
      }.toSet()
  }


  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  companion object {
    private val GROUP = EventLogGroup("maven.plugins", 1)
  }
}

internal class MavenIdValidationRule : CustomValidationRule() {
  override fun getRuleId(): String {
    return "maven-plugin_coordinates"
  }

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    return ValidationResultType.ACCEPTED
  }
}
