package circlet.vcs.clone

import circlet.client.api.*
import circlet.platform.api.*
import circlet.platform.client.*
import circlet.workspaces.*
import com.intellij.util.ui.cloneDialog.*
import libraries.coroutines.extra.*
import runtime.*
import runtime.reactive.*
import runtime.utils.*

class CircletCloneComponentViewModel(
    override val lifetime: Lifetime,
    private val workspace: Workspace,
    private val projectService: Projects,
    private val repositoryService: RepositoryService,
    private val starService: Star
) : Lifetimed {

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
