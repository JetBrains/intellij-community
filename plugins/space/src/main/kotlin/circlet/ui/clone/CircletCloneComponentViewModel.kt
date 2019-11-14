package circlet.ui.clone

import circlet.client.api.*
import circlet.platform.api.*
import circlet.platform.client.*
import circlet.ui.*
import circlet.workspaces.*
import com.intellij.util.concurrency.*
import com.intellij.util.ui.cloneDialog.*
import kotlinx.coroutines.*
import libraries.coroutines.extra.*
import runtime.*
import runtime.reactive.*
import java.awt.image.*

class CircletCloneComponentViewModel(
    override val lifetime: Lifetime,
    private val workspace: Workspace,
    private val projectService: Projects,
    private val repositoryService: RepositoryService,
    private val starService: Star,
    private val circletImageLoader: CircletImageLoader
) : Lifetimed {
    private val appDispatcher = AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()

    val isLoading: MutableProperty<Boolean> = Property.createMutable(false)

    val me: MutableProperty<TD_MemberProfile> = workspace.me

    val profileAvatar: MutableProperty<BufferedImage?> = mapInit(null) {
        val avatarId = me.value.avatar ?: return@mapInit null
        circletImageLoader.loadImageAsync(avatarId).await()
    }

    private fun stopLoading() {
        isLoading.value = false
    }

    private fun startLoading() {
        isLoading.value = true
    }

    val repos = xTransformedPagedListOnFlux<PR_Project, CircletCloneListItem>(
        client = workspace.client,
        batchSize = 10,
        keyFn = { it.id },
        result = { allProjects ->
            val projectsWithRepos = repositoryService.getRepositories(allProjects.map { project -> project.key })
            val starredProjectKeys = starService.starredProjects().map(PR_Project::key).toHashSet()

            val result = mutableListOf<CircletCloneListItem>()
            for (prRepos in projectsWithRepos) {
                val project = prRepos.project

                val isStarred = starredProjectKeys.contains(project.key)

                for (repo in prRepos.repos) {
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
            result
        }
    ) { batch ->
        projectService.projectsBatch(batch, "", "", false)
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
