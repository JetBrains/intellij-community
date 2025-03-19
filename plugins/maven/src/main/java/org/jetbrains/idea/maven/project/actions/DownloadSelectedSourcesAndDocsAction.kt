// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import org.jetbrains.idea.maven.utils.MavenDataKeys

open class DownloadSelectedSourcesAndDocsAction @JvmOverloads constructor(private val mySources: Boolean = true,
                                                                          private val myDocs: Boolean = true) : MavenProjectsAction() {
  override fun isAvailable(e: AnActionEvent): Boolean {
    return super.isAvailable(e) && !getDependencies(e).isEmpty()
  }

  override fun perform(manager: MavenProjectsManager, mavenProjects: List<MavenProject>, e: AnActionEvent) {
    val cs = MavenCoroutineScopeProvider.getCoroutineScope(manager.project)
    cs.launch {
      manager.downloadArtifacts(mavenProjects, getDependencies(e), mySources, myDocs)
    }
  }

  companion object {
    private fun getDependencies(e: AnActionEvent): Collection<MavenArtifact> {
      val result = e.getData(MavenDataKeys.MAVEN_DEPENDENCIES)
      return result ?: emptyList()
    }
  }
}