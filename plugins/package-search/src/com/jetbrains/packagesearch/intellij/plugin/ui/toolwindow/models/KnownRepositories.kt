package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration

internal sealed class KnownRepositories(
    open val repositories: List<RepositoryModel>
) : Collection<RepositoryModel> by repositories {

    fun containsAnyId(ids: Iterable<String>) = ids.any { repoId -> containsId(repoId) }

    fun containsId(id: String) = findById(id) != null

    fun findById(id: String) = find { repo -> repo.id == id }

    fun excludingById(repoIdsToExclude: Iterable<String>) =
        filter { repo -> repoIdsToExclude.contains(repo.id) }

    data class All(override val repositories: List<RepositoryModel>) : KnownRepositories(repositories) {

        fun filterOnlyThoseUsedIn(targetModules: TargetModules) = InTargetModules(
            repositories.filter { repo ->
                if (repo.usageInfo.isEmpty()) return@filter false

                repo.usageInfo.any { usageInfo ->
                    targetModules.modules.map { it.projectModule }
                        .contains(usageInfo.projectModule)
                }
            }, this)

        companion object {

            val EMPTY = All(emptyList())
        }
    }

    data class InTargetModules(
        override val repositories: List<RepositoryModel>,
        val allKnownRepositories: All
    ) : KnownRepositories(repositories) {

        fun repositoryToAddWhenInstallingOrUpgrading(
            project: Project,
            packageModel: PackageModel,
            selectedVersion: PackageVersion
        ): RepositoryModel? {
            if (!PackageSearchGeneralConfiguration.getInstance(project).autoAddMissingRepositories) return null

            val versionRepositoryIds = packageModel.remoteInfo?.versions
                ?.find { it.version == selectedVersion.versionName }
                ?.repositoryIds ?: return null

            if (containsAnyId(versionRepositoryIds)) return null

            return versionRepositoryIds.map { repoId -> allKnownRepositories.findById(repoId) }
                .firstOrNull()
        }

        companion object {

            val EMPTY = InTargetModules(emptyList(), All.EMPTY)
        }
    }
}
