// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs

import circlet.client.api.*
import circlet.client.pr
import circlet.client.repoService
import circlet.platform.client.ConnectionStatus
import circlet.platform.client.resolve
import circlet.workspaces.Workspace
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.space.components.SpaceWorkspaceComponent
import git4idea.GitUtil
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import libraries.coroutines.extra.LifetimeSource
import runtime.async.backoff
import runtime.reactive.MutableProperty
import runtime.reactive.Property
import runtime.reactive.awaitFirst
import runtime.reactive.filter
import runtime.reactive.property.mapInit

@Service
class SpaceProjectContext(project: Project) : Disposable {
  private val lifetime: LifetimeSource = LifetimeSource()

  private val remoteUrls: MutableProperty<Set<GitRemoteUrlCoordinates>> = Property.createMutable(findRemoteUrls(project))

  val context: Property<Context> = lifetime.mapInit(SpaceWorkspaceComponent.getInstance().workspace, remoteUrls, EMPTY) { ws, urls ->
    ws ?: return@mapInit EMPTY
    ws.client.connectionStatus.filter { it is ConnectionStatus.Connected }.awaitFirst(ws.lifetime)
    reloadProjectKeys(ws, urls)
  }

  init {
    project.messageBus
      .connect(this)
      .subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
        val newUrls = findRemoteUrls(project)
        remoteUrls.value = newUrls
      })
  }

  fun getRepoDescriptionByUrl(remoteUrl: String): SpaceRepoInfo? {
    val coordinates = context.value.repoByUrl.keys.find {
      it.url == remoteUrl
    }

    return context.value.repoByUrl[coordinates]
  }
  private fun findRemoteUrls(project: Project): Set<GitRemoteUrlCoordinates> {
    return GitUtil.getRepositoryManager(project).repositories.flatMap { gitRepo ->
      gitRepo.remotes.flatMap { remote ->
        remote.urls.map { url ->
          GitRemoteUrlCoordinates(url, remote, gitRepo)
        }
      }
    }.toSet()
  }

  private suspend fun reloadProjectKeys(ws: Workspace, urls: Set<GitRemoteUrlCoordinates>): Context {
    val reposByUrl: Map<GitRemoteUrlCoordinates, SpaceRepoInfo?> = urls.map { url ->
      backoff {
        url to loadProjectKeysForUrl(ws, url)
      }
    }.toMap()
    val reposInProject = HashMap<SpaceProjectInfo, MutableSet<SpaceRepoInfo>>()

    for (repoDescription in reposByUrl.values.filterNotNull()) {
      for (projectDescription in repoDescription.projectInfos) {
        reposInProject.getOrPut(projectDescription, { HashSet() })
          .add(repoDescription)

      }
    }

    return Context(reposByUrl, reposInProject)
  }

  private suspend fun loadProjectKeysForUrl(ws: Workspace, coordinates: GitRemoteUrlCoordinates): SpaceRepoInfo? {
    val repoService: RepositoryService = ws.client.repoService
    val (repoName, projectKeys) = repoService.findByRepositoryUrl(coordinates.url) ?: return null
    val projectService: Projects = ws.client.pr
    val projectInfos = projectKeys
      .map { SpaceProjectInfo(it, projectService.getProject(it.identifier).resolve()) }
      .toSet()
    return SpaceRepoInfo(coordinates.url,
                         coordinates.remote,
                         coordinates.repository,
                         repoName,
                         projectInfos)
  }

  override fun dispose() {
    lifetime.terminate()
  }

  companion object {
    fun getInstance(project: Project): SpaceProjectContext = project.service()
  }
}

data class Context(
  val repoByUrl: Map<GitRemoteUrlCoordinates, SpaceRepoInfo?>,

  val reposInProject: Map<SpaceProjectInfo, Set<SpaceRepoInfo>>
) {
  val isAssociatedWithSpaceRepository: Boolean
    get() {
      return repoByUrl.values.filterNotNull().any()
    }
}

private val EMPTY: Context = Context(emptyMap(), emptyMap())

data class SpaceRepoInfo(
  val url: String,
  val remote: GitRemote,
  val repository: GitRepository,
  val name: String,
  val projectInfos: Set<SpaceProjectInfo>
)

data class SpaceProjectInfo(
  val key: ProjectKey,
  val project: PR_Project
)

data class GitRemoteUrlCoordinates(val url: String,
                                   val remote: GitRemote,
                                   val repository: GitRepository)