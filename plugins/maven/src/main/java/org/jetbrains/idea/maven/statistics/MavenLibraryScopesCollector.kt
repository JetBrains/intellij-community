// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenProjectsManager

class MavenLibraryScopesCollector : ProjectUsagesCollector() {
  companion object {
    private const val CONTEXT_ANNOTATION_PROCESSOR = "annotation_processor"
    private const val CONTEXT_PARENT = "parent"
    private const val CONTEXT_COMPILER_DEP = "compiler-dependency"
    private const val CONTEXT_DEP = "dependency"
  }

  private val GROUP = EventLogGroup("maven.libraries", 1)
  private val groupArtifactId = EventFields.StringValidatedByCustomRule<MavenLibraryCoordinatesWhitelistValidationRule>("group_artifact_id")
  private val context = EventFields.String("context", listOf(
    CONTEXT_ANNOTATION_PROCESSOR,
    CONTEXT_PARENT,
    CONTEXT_COMPILER_DEP,
    CONTEXT_DEP)
  )
  private val scope = EventFields.String("scope", listOf(
    "",
    "compile",
    "runtime",
    "provided",
    "test",
    "system",
    "import"
  ))
  private val mavenLibraryId = GROUP.registerVarargEvent("maven.libraries.used",
                                                         groupArtifactId, scope, context)

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val manager = MavenProjectsManager.getInstance(project)
    if (!manager.isMavenizedProject) return emptySet()
    val deps = manager.projects.asSequence()
      .flatMap { it.dependencyTree }
      .mapNotNull { it.artifact }
      .map {
        mavenLibraryId.metric(
          groupArtifactId with "${it.groupId}:${it.artifactId}",
          scope with it.scope,
          context with CONTEXT_DEP
        )
      }.toSet()

    val compilerDeps = manager.projects.flatMap {
      it.findPlugin("org.apache.maven.plugins", "maven-compiler-plugin")?.dependencies ?: emptyList()
    }.map {
      mavenLibraryId.metric(
        groupArtifactId with "${it.groupId}:${it.artifactId}",
        scope with "",
        context with CONTEXT_COMPILER_DEP)
    }.toSet()

    val annotationProcessors = manager.projects.flatMap {
      it.externalAnnotationProcessors
    }.map {
      mavenLibraryId.metric(
        groupArtifactId with "${it.groupId}:${it.artifactId}",
        scope with "",
        context with CONTEXT_ANNOTATION_PROCESSOR)
    }.toSet()

    val parents = manager.projects.mapNotNull {
      it.parentId
    }.map {
      mavenLibraryId.metric(
        groupArtifactId with "${it.groupId}:${it.artifactId}",
        scope with "",
        context with CONTEXT_PARENT)
    }.toSet()



    return deps + compilerDeps + annotationProcessors + parents
  }


  override fun getGroup(): EventLogGroup = GROUP
}


class MavenLibraryCoordinatesWhitelistValidationRule : MavenWhitelistRule("maven_libraries_rule_whitelist_ids",
                                                                          "/org/jetbrains/idea/maven/statistics/maven-whitelist-libraries.txt")
