// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.ExternalSystemCoordinateContributor
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.model.MavenCoordinate
import org.jetbrains.idea.maven.project.MavenProjectsManager

@ApiStatus.Internal
class MavenCoordinateContributor : ExternalSystemCoordinateContributor {

  override fun findModuleCoordinate(module: Module): ProjectCoordinate? {
    val project = module.getProject()
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    val mavenProject = mavenProjectsManager.findProject(module) ?: return null
    return mavenProject.mavenId.toProjectCoordinate()
  }

  private fun MavenCoordinate.toProjectCoordinate(): ProjectCoordinate {
    return ProjectId(groupId, artifactId, version)
  }
}
