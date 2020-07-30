package com.intellij.space.vcs

import circlet.client.api.PR_Project
import circlet.client.api.ProjectKey
import circlet.client.api.Projects
import circlet.client.api.RepositoryService
import circlet.client.pr
import circlet.client.repoService
import com.intellij.space.components.space
import circlet.platform.client.ConnectionStatus
import circlet.platform.client.resolve
import circlet.workspaces.Workspace
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import git4idea.GitUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import libraries.coroutines.extra.LifetimeSource
import runtime.async.backoff
import runtime.reactive.*

class SpaceProjectContext(project: Project) : Disposable {
  private val lifetime: LifetimeSource = LifetimeSource()

  private val remoteUrls: MutableProperty<Set<String>> = Property.createMutable(findRemoteUrls(project))

  val context: Property<Context> = lifetime.mapInit(space.workspace, remoteUrls, EMPTY) { ws, urls ->
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
    return context.value.repoByUrl[remoteUrl]
  }

  private fun findRemoteUrls(project: Project): Set<String> = GitUtil.getRepositoryManager(project).repositories
    .flatMap { it.remotes }
    .flatMap { it.urls }
    .toSet<String>()

  private suspend fun reloadProjectKeys(ws: Workspace, urls: Set<String>): Context {
    val reposByUrl: Map<String, SpaceRepoInfo?> = urls.map { url ->
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

  private suspend fun loadProjectKeysForUrl(ws: Workspace, url: String): SpaceRepoInfo? {
    val repoService: RepositoryService = ws.client.repoService
    val (repoName, projectKeys) = repoService.findByRepositoryUrl(url) ?: return null
    val projectService: Projects = ws.client.pr
    val projectDescriptions = projectKeys.map { SpaceProjectInfo(it, projectService.getProjectByKey(it).resolve()) }.toSet()
    return SpaceRepoInfo(url,
                         repoName,
                         projectDescriptions)
  }

  override fun dispose() {
    lifetime.terminate()
  }

  companion object {
    fun getInstance(project: Project): SpaceProjectContext {
      return project.getService(SpaceProjectContext::class.java)
    }
  }
}

data class Context(
  val repoByUrl: Map<String, SpaceRepoInfo?>,

  val reposInProject: Map<SpaceProjectInfo, Set<SpaceRepoInfo>>
) {
  val empty: Boolean
    get() {
      return this == EMPTY
    }
}

private val EMPTY: Context = Context(emptyMap(), emptyMap())

data class SpaceRepoInfo(
  val url: String,
  val name: String,
  val projectInfos: Set<SpaceProjectInfo>
)

data class SpaceProjectInfo(
  val key: ProjectKey,
  val project: PR_Project
)
