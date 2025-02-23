// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.java.library.MavenCoordinates
import com.intellij.java.library.getMavenCoordinates
import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.ExternalSystemCoordinateContributor
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.importing.MavenImportUtil.getMavenModuleType
import org.jetbrains.idea.maven.model.MavenCoordinate
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.jps.model.serialization.SerializationConstants

@ApiStatus.Internal
class MavenCoordinateContributor : ExternalSystemCoordinateContributor {

  override fun findModuleCoordinate(module: Module): ProjectCoordinate? {
    val mavenProject = findMavenProject(module) ?: return null
    val mavenProjectCoordinate = mavenProject.mavenId.toProjectCoordinate()
    return when (module.getMavenModuleType()) {
      StandardMavenModuleType.SINGLE_MODULE -> mavenProjectCoordinate
      StandardMavenModuleType.MAIN_ONLY -> mavenProjectCoordinate
      else -> null
    }
  }

  private fun findMavenProject(module: Module): MavenProject? {
    val project = module.getProject()
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    return mavenProjectsManager.findProject(module)
  }

  private fun MavenCoordinate.toProjectCoordinate(): ProjectCoordinate {
    return ProjectId(groupId, artifactId, version)
  }

  override fun findLibraryCoordinate(library: Library): ProjectCoordinate? {
    val librarySource = library.externalSource ?: return null
    if (librarySource.id != SerializationConstants.MAVEN_EXTERNAL_SOURCE_ID) {
      return null
    }
    val libraryName = library.name ?: return null
    if (libraryName.startsWith(SHADED_MAVEN_LIBRARY_NAME_PREFIX)) {
      return null
    }
    return library.getMavenCoordinates()
      ?.takeIf { it.classifier == null }
      ?.toProjectCoordinate()
  }

  private fun MavenCoordinates.toProjectCoordinate(): ProjectCoordinate {
    return ProjectId(groupId, artifactId, version)
  }
}
