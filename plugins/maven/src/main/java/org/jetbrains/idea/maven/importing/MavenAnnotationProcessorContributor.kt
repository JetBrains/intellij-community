// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.project.Project
import com.intellij.util.ExceptionUtil
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.importing.MavenAnnotationProcessorConfiguratorUtil.getProcessorArtifactInfos
import org.jetbrains.idea.maven.importing.MavenImportUtil.compilerConfigsForCompilePhase
import org.jetbrains.idea.maven.model.MavenArtifactInfo
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectResolutionContributor
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenResolveResultProblemProcessor
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper
import org.jetbrains.idea.maven.utils.MavenJDOMUtil

internal class MavenAnnotationProcessorContributor : MavenProjectResolutionContributor {
  override suspend fun onMavenProjectResolved(project: Project, mavenProject: MavenProject, embedder: MavenEmbedderWrapper) {
    val artifactsInfo = mavenProject.compilerConfigsForCompilePhase()
      .mapNotNull { MavenJDOMUtil.findChildByPath(it, "annotationProcessorPaths") }
      .flatMap { getProcessorArtifactInfos(it, mavenProject) }


    val externalArtifacts: MutableList<MavenArtifactInfo> = ArrayList()
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    val tree = mavenProjectsManager.projectsTree
    for (info in artifactsInfo) {
      val mavenArtifact = tree.findProject(MavenId(info.groupId, info.artifactId, info.version))
      if (mavenArtifact == null) {
        if (!externalArtifacts.contains(info)) {
          externalArtifacts.add(info)
        }
      }
    }

    try {
      val annotationProcessors = embedder.resolveArtifactsTransitively(ArrayList(externalArtifacts),
                                                                       ArrayList(mavenProject.remoteRepositories))
      if (annotationProcessors.problem != null) {
        MavenResolveResultProblemProcessor.notifySyncForProblem(project, annotationProcessors.problem!!)
      }
      else {
        mavenProject.addAnnotationProcessors(annotationProcessors.mavenResolvedArtifacts)
      }
    }
    catch (e: Exception) {
      val message = if (e.message != null) e.message!! else ExceptionUtil.getThrowableText(e)
      MavenProjectsManager.getInstance(project).syncConsole
        .addWarning(SyncBundle.message("maven.sync.annotation.processor.problem"), message)
    }
  }
}