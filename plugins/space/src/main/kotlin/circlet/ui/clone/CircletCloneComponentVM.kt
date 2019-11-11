package circlet.ui.clone

import circlet.client.api.*
import circlet.platform.client.*
import libraries.coroutines.extra.*
import runtime.reactive.*

class CircletCloneComponentVM(
    val pr: Projects,
    val repo: RepositoryService,
    val star: Star,
    val client: KCircletClient,
    override val lifetime: Lifetime
) : Lifetimed {
    val allProjectsWithReposFlux: Property<XPagedListOnFlux<PR_ProjectRepos>?> = mapInit(null) {
        xTransformedPagedListOnFlux<PR_Project, PR_ProjectRepos>(
            client = client,
            batchSize = 100,
            keyFn = { it.id },
            result = {
                it.let { repo.getRepositories(it.map { it.key }) }
            }
        ) {
            pr.projectsBatch(it, tag = null, term = "", exceptStarred = true)
        }.apply {
            more()
        }
    }

    private val starredProjects: Property<List<PR_Project>?> = mapInit(null) {
        star.starredProjects()
    }

    // TODO: do not load repos for starred projects for the second time (they are already loaded in allProjectsWithReposFlux)
    val starredProjectsWithRepos = mapInit(starredProjects, null) { projects ->
        projects?.let { repo.getRepositories(it.map { it.key }) }
    }
}
