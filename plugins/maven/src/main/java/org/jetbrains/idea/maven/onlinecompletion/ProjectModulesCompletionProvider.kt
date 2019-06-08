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
  override fun findGroupCandidates(template: MavenCoordinate, searchParameters: SearchParameters): List<MavenDependencyCompletionItem> {
    return doQuery(template) {
      startWith(it.groupId, template.groupId)
    }
  }


  private fun doQuery(template: MavenCoordinate, predicate: (MavenCoordinate) -> Boolean): List<MavenDependencyCompletionItem> {
    return MavenProjectsManager.getInstance(myProject).projects.asSequence()
      .map { it.getMavenId() }
      .filter(predicate)
      .map { MavenDependencyCompletionItem(it.groupId, it.artifactId, it.version, PROJECT) }
      .toList()
  }

  @Throws(IOException::class)
  override fun findArtifactCandidates(template: MavenCoordinate, searchParameters: SearchParameters): List<MavenDependencyCompletionItem> {
    return doQuery(template) {
      equals(it.groupId, template.groupId) && startWith(it.artifactId, template.artifactId);
    }
  }

  @Throws(IOException::class)
  override fun findAllVersions(template: MavenCoordinate, searchParameters: SearchParameters): List<MavenDependencyCompletionItem> {
    return doQuery(template) {
      equals(it.groupId, template.groupId) && equals(it.artifactId, template.artifactId);
    }
  }

  @Throws(IOException::class)
  override fun findClassesByString(str: String, searchParameters: SearchParameters): List<MavenDependencyCompletionItemWithClass> {
    return emptyList();
  }

  private fun startWith(str: String?, prefix: String?): Boolean {
    if (str == null) {
      return false;
    }
    if (prefix.isNullOrBlank()) return true;

    return str.startsWith(prefix);
  }

  private fun equals(str1: String?, str2: String?): Boolean {
    if (str1 == null || str2 == null) {
      return false;
    }
    return str1 == str2;
  }
}
