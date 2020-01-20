package circlet.vcs

import circlet.client.*
import circlet.client.api.*
import circlet.components.*
import circlet.platform.client.*
import circlet.workspaces.*
import com.intellij.openapi.*
import com.intellij.openapi.project.*
import git4idea.*
import git4idea.repo.*
import libraries.coroutines.extra.*
import runtime.async.*
import runtime.reactive.*

class CircletProjectContext(project: Project) : Disposable {
    private val lifetime: LifetimeSource = LifetimeSource()

    private val remoteUrls: MutableProperty<Set<String>> = Property.createMutable(findRemoteUrls(project))

    private val projectsInfo: Property<Map<String, Pair<String, List<CircletProjectDescription>>?>> = lifetime.mapInit(circletWorkspace.workspace, remoteUrls, emptyMap()) { ws, urls ->
        ws ?: return@mapInit emptyMap<String, Pair<String, List<CircletProjectDescription>>?>()
        ws.client.connectionStatus.filter { it is ConnectionStatus.Connected }.awaitFirst(ws.lifetime)
        reloadProjectKeys(ws, urls)
    }

    val projectDescriptions: Pair<String, List<CircletProjectDescription>>?
        get() = projectsInfo.value.values.filterNotNull().firstOrNull()

    init {
        project.messageBus
            .connect(this)
            .subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
                val newUrls = findRemoteUrls(project)
                remoteUrls.value = newUrls
            })
    }

    fun findProjectInfo(remoteUrl: String): Pair<String, List<CircletProjectDescription>>? {
        return projectsInfo.value[remoteUrl]
    }

    private fun findRemoteUrls(project: Project): Set<String> = GitUtil.getRepositoryManager(project).repositories
        .flatMap { it.remotes }
        .flatMap { it.urls }
        .toSet<String>()

    private suspend fun reloadProjectKeys(ws: Workspace, urls: Set<String>): Map<String, Pair<String, List<CircletProjectDescription>>?> {
        return urls.map { url ->
            backoff {
                url to loadProjectKeysForUrl(ws, url)
            }
        }.toMap()
    }

    private suspend fun loadProjectKeysForUrl(ws: Workspace, url: String): Pair<String, List<CircletProjectDescription>>? {
        val repoService: RepositoryService = ws.client.repoService
        val repoProjectKeys = repoService.findByRepositoryUrl(url) ?: return null
        val projectService: Projects = ws.client.pr
        val list = repoProjectKeys.second.map { CircletProjectDescription(it, projectService.getProjectByKey(it).resolve()) }.toList()
        return repoProjectKeys.first to list
    }

    override fun dispose() {
        lifetime.terminate()
    }

    companion object {
        fun getInstance(project: Project): CircletProjectContext {
            return project.getService(CircletProjectContext::class.java)
        }
    }
}
