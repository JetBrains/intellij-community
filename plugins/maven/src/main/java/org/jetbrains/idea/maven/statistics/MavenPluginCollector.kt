// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenProjectsManager

class MavenPluginCollector : ProjectUsagesCollector() {
  private val GROUP = EventLogGroup("maven.plugins", 5)

  private val groupArtifactId = EventFields.StringValidatedByCustomRule<MavenPluginCoordinatesWhitelistValidationRule>("group_artifact_id")
  private val pluginVersion = EventFields.Version
  private val isExtension = EventFields.Boolean("extension")
  private val hasConfiguration = EventFields.Boolean("has_configuration")


  private val mavenPluginId = group.registerVarargEvent("maven.plugins.used",
                                                        groupArtifactId, pluginVersion, isExtension, hasConfiguration)

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val manager = MavenProjectsManager.getInstance(project)
    if (!manager.isMavenizedProject) return emptySet()
    return manager.projects.asSequence()
      .flatMap { it.declaredPlugins }
      .map {
        mavenPluginId.metric(
          groupArtifactId with "${it.groupId}:${it.artifactId}",
          pluginVersion with it.version,
          isExtension with it.isExtensions,
          hasConfiguration with (it.configurationElement != null)
        )
      }.toSet()
  }

  override fun getGroup(): EventLogGroup = GROUP
}


class MavenPluginCoordinatesWhitelistValidationRule : MavenWhitelistRule("maven_plugin_rule_whitelist_ids",
                                                                         "/org/jetbrains/idea/maven/statistics/maven-whitelist-plugins.txt")
