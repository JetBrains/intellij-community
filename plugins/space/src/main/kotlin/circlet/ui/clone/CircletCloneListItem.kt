package circlet.ui.clone

import circlet.client.api.*

internal data class CircletCloneListItem(
    val project: PR_Project,
    val starred: Boolean,
    val repoInfo: PR_RepositoryInfo,
    val repoDetails: RepoDetails
)
