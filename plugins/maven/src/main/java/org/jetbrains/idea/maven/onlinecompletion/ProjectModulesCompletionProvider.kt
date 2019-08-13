// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion

import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.model.MavenCoordinate
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem.Type.PROJECT
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItemWithClass
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.io.IOException

class ProjectModulesCompletionProvider(private val myProject: Project) : DependencyCompletionProvider {

  @Throws(IOException::class)
  override fun findGroupCandidates(template: MavenCoordinate, searchParameters: SearchParameters) = modulesAsMavenId()
  override fun findArtifactCandidates(template: MavenCoordinate, searchParameters: SearchParameters) = modulesAsMavenId()
  override fun findAllVersions(template: MavenCoordinate, searchParameters: SearchParameters) = modulesAsMavenId()

  private fun modulesAsMavenId(): List<MavenDependencyCompletionItem> {
    return MavenProjectsManager.getInstance(myProject).projects.asSequence()
      .map { it.mavenId }
      .map { MavenDependencyCompletionItem(it.groupId, it.artifactId, it.version, PROJECT) }
      .toList()
  }

  @Throws(IOException::class)
  override fun findClassesByString(str: String, searchParameters: SearchParameters): List<MavenDependencyCompletionItemWithClass> {
    return emptyList()
  }
}
