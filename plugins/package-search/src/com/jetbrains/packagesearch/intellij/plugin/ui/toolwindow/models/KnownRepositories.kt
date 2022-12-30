/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration

internal sealed class KnownRepositories(
    open val repositories: List<RepositoryModel>
) : Collection<RepositoryModel> by repositories {

    fun containsAnyId(ids: Iterable<String>) = ids.any { repoId -> containsId(repoId) }

    private fun containsId(id: String) = findById(id) != null

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
            }, this
        )

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
