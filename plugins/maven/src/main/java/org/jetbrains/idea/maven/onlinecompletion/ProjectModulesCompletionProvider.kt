// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion

import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.reposearch.DependencySearchProvider
import org.jetbrains.idea.reposearch.RepositoryArtifactData
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class ProjectModulesCompletionProvider(private val myProject: Project) : DependencySearchProvider {

  override fun fulltextSearch(searchString: String) = getLocal()

  override fun suggestPrefix(groupId: String?, artifactId: String?) = getLocal()

  private fun getLocal(): CompletableFuture<List<RepositoryArtifactData>> =
    CompletableFuture.supplyAsync {
      MavenProjectsManager.getInstance(myProject).projects.asSequence()
        .map { MavenDependencyCompletionItem(it.mavenId.key) }
        .filter { it.groupId != null && it.artifactId != null }
        .map { MavenRepositoryArtifactInfo(it.groupId!!, it.artifactId!!, arrayOf(it)) }
        .toList()
    }

  override fun isLocal() = true
}
