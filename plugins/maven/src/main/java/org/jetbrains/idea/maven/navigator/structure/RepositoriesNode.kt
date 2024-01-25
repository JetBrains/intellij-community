// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure

import com.intellij.util.containers.mapSmart
import icons.MavenIcons
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.server.MavenIndexUpdateState
import java.util.concurrent.CopyOnWriteArrayList

internal class RepositoriesNode(structure: MavenProjectsStructure, parent: ProjectNode) : GroupNode(structure, parent) {
  private val myRepositoryNodes = CopyOnWriteArrayList<RepositoryNode>()

  init {
    templatePresentation.setIcon(MavenIcons.MavenRepoFolder)
  }

  override fun getName(): String {
    return MavenProjectBundle.message("view.node.repositories")
  }

  override fun doGetChildren(): List<MavenSimpleNode?> {
    return myRepositoryNodes
  }

  fun updateRepositories(mavenProject: MavenProject) {
    val local = mavenProject.localRepository
    val remotes = mavenProject.remoteRepositories
    myRepositoryNodes.clear()
    myRepositoryNodes.add(RepositoryNode(myMavenProjectsStructure, this, "local", local.absolutePath, true))
    myRepositoryNodes.addAll(remotes.mapSmart { RepositoryNode(myMavenProjectsStructure, this, it.id, it.url, false) })
  }

  fun updateStatus(state: MavenIndexUpdateState) {
    val nodesToUpdate = myRepositoryNodes.filter { it.url == state.myUrl }
    if (nodesToUpdate.isEmpty()) return;

    nodesToUpdate.forEach {
      it.update()
    }
  }
}
