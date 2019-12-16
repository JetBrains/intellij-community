package circlet.vcs.clone

import circlet.client.*
import circlet.client.api.*
import circlet.platform.api.*
import circlet.platform.client.*
import circlet.settings.*
import circlet.settings.CloneType.*
import circlet.vcs.*
import circlet.workspaces.*
import com.intellij.util.ui.cloneDialog.*
import libraries.coroutines.extra.*
import runtime.*
import runtime.reactive.*

internal class CircletCloneComponentViewModel(
    override val lifetime: Lifetime,
    workspace: Workspace
) : Lifetimed {
    private val projectService: Projects = workspace.client.pr
    private val repositoryService: RepositoryService = workspace.client.repoService
    private val starService: Star = workspace.client.star
    private val td: TeamDirectory = workspace.client.td
    private val ssh: SshKeys = workspace.client.ssh

    val isLoading: MutableProperty<Boolean> = Property.createMutable(false)

    val me: MutableProperty<TD_MemberProfile> = workspace.me

    val repos = xTransformedPagedListOnFlux<PR_Project, CircletCloneListItem?>(
        client = workspace.client,
        batchSize = 10,
        keyFn = { it.id },
        result = { allProjects ->

            val projectsWithRepos = repositoryService.getRepositories(allProjects.map { project -> project.key }).groupBy { it.project.key }

            val starredProjectKeys = starService.starredProjects().resolveAll().map(PR_Project::key).toHashSet()

            val result = mutableListOf<CircletCloneListItem?>()
            allProjects.forEach { project ->
                val projectRepos = projectsWithRepos[project.key]
                if (projectRepos != null) {
                    val isStarred = starredProjectKeys.contains(project.key)
                    projectRepos.forEach { projectRepo ->
                        projectRepo.repos.forEach { repo ->
                            val detailsProperty = mutableProperty<RepoDetails?>(null)
                            val item = CircletCloneListItem(project, isStarred, repo, detailsProperty)
                            item.visible.forEach(lifetime) {
                                item.visible.forEach(lifetime) {
                                    launch(lifetime, Ui) {
                                        if (it) {
                                            detailsProperty.value = repositoryService.repositoryDetails(project.key, repo.name)
                                        }
                                    }
                                }
                            }
                            result.add(item)
                        }
                    }
                }
            }
            while (result.size < allProjects.size) {
                result.add(null)
            }
            result
        }
    ) { batch ->
        projectService.projectsBatch(batch, "", "").map { it.resolve() }
    }

    val cloneType: MutableProperty<CloneType> = Property.createMutable(CircletSettings.getInstance().cloneType)

    val selectedUrl: MutableProperty<String?> = Property.createMutable(null)

    val circletHttpPasswordState: MutableProperty<CircletHttpPasswordState> = lifetime.mapInit<CloneType, CircletHttpPasswordState>(cloneType, CircletHttpPasswordState.NotChecked) { cloneType ->
        if (cloneType == SSH) return@mapInit CircletHttpPasswordState.NotChecked

        td.getVcsPassword(me.value.id).let {
            if (it == null) CircletHttpPasswordState.NotSet else CircletHttpPasswordState.Set(it)
        }
    } as MutableProperty<CircletHttpPasswordState>

    val circletKeysState: MutableProperty<CircletKeysState> = run {
        val property: MutableProperty<CircletKeysState> = mutableProperty(CircletKeysState.NotChecked)
        UiDispatch.dispatchInterval(1000, lifetime) {
            launch(lifetime, Ui) {
                property.value = loadSshState(cloneType.value)
            }
        }
        property
    }

    private suspend fun loadSshState(cloneType: CloneType): CircletKeysState {
        if (cloneType == HTTP) return CircletKeysState.NotChecked

        return ssh.sshKeys(me.value.id).let {
            if (it.isNullOrEmpty()) CircletKeysState.NotSet else CircletKeysState.Set(it)
        }
    }

    val readyToClone: Property<Boolean> = lifetime.mapInit(selectedUrl, circletHttpPasswordState, circletKeysState, false) { url, http, ssh ->
        url != null && (http is CircletHttpPasswordState.Set || ssh is CircletKeysState.Set)
    }
}

data class CircletCloneListItem(
    val project: PR_Project,
    val starred: Boolean,
    val repoInfo: PR_RepositoryInfo,
    val repoDetails: Property<RepoDetails?>
) : SearchableListItem, Comparable<CircletCloneListItem> {

    val visible = mutableProperty(false)

    override val stringToSearch: String
        get() = repoInfo.name

    override fun compareTo(other: CircletCloneListItem): Int {
        if (starred != other.starred) {
            return other.starred.compareTo(starred)
        }
        if (project.name != other.project.name) {
            return project.name.compareTo(other.project.name)
        }
        return repoInfo.name.compareTo(other.repoInfo.name)
    }
}
