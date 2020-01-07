package circlet.vcs.clone

import circlet.client.api.*
import circlet.platform.api.*
import circlet.platform.client.*
import circlet.workspaces.*
import com.intellij.util.ui.cloneDialog.*
import libraries.coroutines.extra.*
import runtime.*
import runtime.reactive.*

class CircletCloneComponentViewModel(
    override val lifetime: Lifetime,
    private val workspace: Workspace,
    private val projectService: Projects,
    private val repositoryService: RepositoryService,
    private val starService: Star
) : Lifetimed {

    val isLoading: MutableProperty<Boolean> = Property.createMutable(false)

    val me: MutableProperty<TD_MemberProfile> = workspace.me

    val repos = xTransformedPagedListOnFlux<PR_Project, CircletCloneListItem?>(
        client = workspace.client,
        batchSize = 10,
        keyFn = { it.id },
        result = { allProjects ->

            val projectsWithRepos = repositoryService.getRepositories(allProjects.map { project -> project.key }).groupBy { it.project.key }

            val starredProjectKeys = starService.starredProjects().map(PR_Project::key).toHashSet()

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
            while(result.size < allProjects.size) {
                result.add(null)
            }
            result
        }
    ) { batch ->
        projectService.projectsBatch(batch, "", "").map { it.resolve() }
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
