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
import runtime.utils.*

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

    val repos = xTransformedPagedListOnFlux<Ref<PR_Project>, CircletCloneListItem?>(
        client = workspace.client,
        batchSize = 10,
        keyFn = { it.id },
        result = { allProjectRefs ->
            val allProjectsWithRepos = workspace.client.arena.resolveRefsOrFetch {
                allProjectRefs.map { it to  it.extensionRef(ProjectReposRecord::class) }
            }
            val starredProjectIds = starService.starredProjects().mapToSet(Ref<PR_Project>::id)

            val items = allProjectsWithRepos
                .asSequence()
                .map { (projectRef, reposRef) ->
                    object {
                        val project = projectRef.resolve()
                        val repos = reposRef.resolve().repos
                        val isStarred = projectRef.id in starredProjectIds
                    }
                }
                .flatMap {
                    it.repos
                        .asSequence()
                        .map { repo ->
                            val detailsProperty = mutableProperty<RepoDetails?>(null)
                            val item = CircletCloneListItem(it.project, it.isStarred, repo, detailsProperty)
                            item.visible.forEach(lifetime) { visible ->
                                launch(lifetime, Ui) {
                                    if (visible) {
                                        detailsProperty.value = repositoryService.repositoryDetails(it.project.key, repo.name)
                                    }
                                }
                            }
                            item
                        }
                }

            mutableListOf<CircletCloneListItem?>().apply {
                addAll(items)
                if (size < allProjectRefs.size) {
                    add(null)
                }
            }
        }
    ) { batch ->
        projectService.projectsBatch(batch, "", "")
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
