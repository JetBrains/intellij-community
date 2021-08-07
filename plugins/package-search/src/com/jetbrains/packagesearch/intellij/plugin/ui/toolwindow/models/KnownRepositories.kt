package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

internal sealed class KnownRepositories(
    open val repositories: List<RepositoryModel>
) : Collection<RepositoryModel> by repositories {

    fun containsAnyId(ids: Iterable<String>) = ids.any { repoId -> containsId(repoId) }

    fun containsId(id: String) = findById(id) != null

    fun findById(id: String) = find { repo -> repo.id == id }

    fun excludingById(repoIdsToExclude: Iterable<String>) =
        repositories.filter { repo -> repoIdsToExclude.contains(repo.id) }

    data class All(override val repositories: List<RepositoryModel>) : KnownRepositories(repositories) {

        fun filterOnlyThoseUsedIn(targetModules: TargetModules) = InTargetModules(
            repositories.filter { repo ->
                if (repo.usageInfo.isEmpty()) return@filter false

                repo.usageInfo.any { usageInfo ->
                    targetModules.modules.map { it.projectModule }
                        .contains(usageInfo.projectModule)
                }
            }
        )

        companion object {

            val EMPTY = All(emptyList())
        }
    }

    data class InTargetModules(override val repositories: List<RepositoryModel>) : KnownRepositories(repositories) {

        fun repositoryToAddWhenInstallingOrUpgrading(
            packageModel: PackageModel,
            selectedVersion: PackageVersion,
            allKnownRepositories: All
        ): RepositoryModel? {
            val versionRepositoryIds = packageModel.remoteInfo?.versions
                ?.find { it.version == selectedVersion.versionName }
                ?.repositoryIds ?: return null

            if (containsAnyId(versionRepositoryIds)) return null

            return versionRepositoryIds.map { repoId -> allKnownRepositories.findById(repoId) }
                .firstOrNull()
        }

        companion object {

            val EMPTY = InTargetModules(emptyList())
        }
    }
}
