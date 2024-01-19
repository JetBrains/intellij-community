// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.converters

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.xml.ConvertContext
import org.jetbrains.idea.maven.indices.MavenIndicesManager
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.resolved
import org.jetbrains.idea.reposearch.DependencySearchService

class MavenArtifactCoordinatesArtifactIdConverter : MavenArtifactCoordinatesConverter() {
  override fun doIsValid(id: MavenId, manager: MavenIndicesManager, context: ConvertContext): Boolean {
    if (StringUtil.isEmpty(id.groupId) || StringUtil.isEmpty(id.artifactId)) return false

    val projectsManager = MavenProjectsManager.getInstance(context.project)
    if (StringUtil.isNotEmpty(id.version)) {
      if (projectsManager.findProject(id) != null) return true
    }
    else {
      for (project in projectsManager.projects) {
        val mavenId = project.mavenId
        if (id.groupId == mavenId.groupId && id.artifactId == mavenId.artifactId) return true
      }
    }

    // Check if artifact was found on importing.
    val projectFile = getMavenProjectFile(context)
    val mavenProject = if (projectFile == null) null else projectsManager.findProject(projectFile)
    if (mavenProject != null) {
      for (artifact in mavenProject.findDependencies(id.groupId, id.artifactId)) {
        if (artifact.resolved()) {
          return true
        }
      }
    }

    val hasLocalArtifactId = manager.hasLocalArtifactId(id.groupId, id.artifactId)
    MavenLog.LOG.trace("local index artifact id $id: $hasLocalArtifactId")
    return hasLocalArtifactId
  }

  override fun doGetVariants(id: MavenId, searchService: DependencySearchService): Set<String> {
    return emptySet()
  }
}
