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


  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  companion object {
    private val GROUP = EventLogGroup("maven.plugins", 4)

    val groupArtifactId = EventFields.StringValidatedByCustomRule<MavenPluginCoordinatesWhitelistValidationRule>("group_artifact_id")
    val version = EventFields.StringValidatedByCustomRule<MavenPluginVersionValidationRule>("version")
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

class MavenPluginVersionValidationRule : CustomValidationRule() {
  override fun getRuleId(): String {
    return "maven_plugin_version_validation_rule"
  }

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val splitted = data.split('.')
    if (splitted.isEmpty()) return ValidationResultType.REJECTED
    if (!splitted[0].all { it.isDigit() }) return ValidationResultType.REJECTED
    if (splitted.size > 1 && !splitted[1].all { it.isDigit() }) return ValidationResultType.REJECTED
    return ValidationResultType.ACCEPTED
  }
}
