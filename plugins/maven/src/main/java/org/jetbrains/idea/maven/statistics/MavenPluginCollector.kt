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

  private val mavenPluginId = group.registerVarargEvent("maven.plugins.used",
                                                        groupArtifactId, Companion.version, isExtension, hasConfiguration)

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val manager = MavenProjectsManager.getInstance(project)
    if (!manager.isMavenizedProject) return emptySet()
    return manager.projects.asSequence()
      .flatMap { it.declaredPlugins }
      .map {
        mavenPluginId.metric(
          groupArtifactId with "${it.groupId}:${it.artifactId}",
          Companion.version with it.version,
          isExtension with it.isExtensions,
          hasConfiguration with (it.configurationElement != null)
        )
      }.toSet()
  }

  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("maven.plugins", 5)

    val groupArtifactId = EventFields.StringValidatedByCustomRule<MavenPluginCoordinatesWhitelistValidationRule>("group_artifact_id")
    val version = EventFields.Version
    val isExtension = EventFields.Boolean("extension")
    val hasConfiguration = EventFields.Boolean("has_configuration")
  }
}


class MavenPluginCoordinatesWhitelistValidationRule : CustomValidationRule() {
  private val whiteList: Set<String>

  init {
    val url = this::class.java.getResource("/org/jetbrains/idea/maven/statistics/maven-whitelist-plugins.txt")
    whiteList = url
                  ?.readText()
                  ?.lines()
                  ?.asSequence()
                  ?.filter { it.isNotBlank() }
                  ?.map { it.trim() }
                  ?.filter { !it.startsWith('#') }
                  ?.toSet() ?: emptySet()

  }

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    return if (data in whiteList) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
  }

  override fun getRuleId(): String {
    return "maven_plugin_rule_whitelist_ids"
  }

}
