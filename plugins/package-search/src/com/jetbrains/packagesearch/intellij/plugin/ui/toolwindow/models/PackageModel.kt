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

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.normalizeWhitespace
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.PackageVersionNormalizer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.packagesearch.api.v2.ApiStandardPackage

internal sealed class PackageModel(
    val groupId: String,
    val artifactId: String,
    val remoteInfo: ApiStandardPackage?,
    val remoteVersions: List<NormalizedPackageVersion<PackageVersion.Named>>
) : Comparable<PackageModel> {

    val identifier = PackageIdentifier("$groupId:$artifactId")

    val sortKey = (remoteInfo?.name.normalizeWhitespace() ?: identifier.rawValue.lowercase())

    val isKotlinMultiplatform = remoteInfo?.mpp != null

    fun getAvailableVersions(onlyStable: Boolean): List<NormalizedPackageVersion<*>> {
        val allVersions = declaredVersions.union(remoteVersions)

        return allVersions.asSequence()
            .filter { if (onlyStable) it.isStable else true }
            .distinctBy { it.versionName }
            .sortedDescending()
            .toList()
    }

    protected abstract val declaredVersions: List<NormalizedPackageVersion<*>>

    override fun compareTo(other: PackageModel): Int = sortKey.compareTo(other.sortKey)

    abstract val searchableInfo: String

    class Installed(
        groupId: String,
        artifactId: String,
        remoteInfo: ApiStandardPackage?,
        remoteVersions: List<NormalizedPackageVersion<PackageVersion.Named>>,
        val usageInfo: List<DependencyUsageInfo>,
        val latestInstalledVersion: NormalizedPackageVersion<*>,
        override val declaredVersions: List<NormalizedPackageVersion<*>>
    ) : PackageModel(groupId, artifactId, remoteInfo, remoteVersions) {

        companion object {

            suspend operator fun invoke(
                groupId: String,
                artifactId: String,
                remoteInfo: ApiStandardPackage?,
                usageInfo: List<DependencyUsageInfo>,
                normalizer: PackageVersionNormalizer
            ) = coroutineScope {
                val remoteVersions = async { evaluateRemoteVersions(remoteInfo, normalizer) }
                val latestInstalledVersion = async {
                    usageInfo.map { it.declaredVersion }.maxOfOrNull { normalizer.parse(it) }
                        ?: error("An installed package must always have at least one usage")
                }
                val declaredVersions = async {
                    usageInfo.map { it.declaredVersion }.map { normalizer.parse(it) }
                }
                Installed(
                    groupId,
                    artifactId,
                    remoteInfo,
                    remoteVersions.await(),
                    usageInfo,
                    latestInstalledVersion.await(),
                    declaredVersions.await()
                )
            }
        }

        init {
            require(usageInfo.isNotEmpty()) { "An installed package must always have at least one usage" }
        }

        fun findUsagesIn(moduleModels: List<ModuleModel>): List<DependencyUsageInfo> =
            findUsagesIn(moduleModels.map { it.projectModule })

        private fun findUsagesIn(projectModules: Collection<ProjectModule>): List<DependencyUsageInfo> {
            if (projectModules.isEmpty()) return emptyList()
            return usageInfo.filter { usageInfo -> projectModules.any { it == usageInfo.projectModule } }
        }

        fun copyWithUsages(usages: List<DependencyUsageInfo>) =
            Installed(groupId, artifactId, remoteInfo, remoteVersions, usages, latestInstalledVersion, declaredVersions)

        override val searchableInfo =
            buildString {
                appendLine(identifier)
                for (usage in usageInfo) {
                    appendLine(usage.declaredVersion)
                }

                if (remoteInfo != null) {
                    appendLine(remoteInfo.description)
                    appendLine(remoteInfo.name)
                }
            }.lowercase()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Installed) return false

            if (usageInfo != other.usageInfo) return false
            if (latestInstalledVersion != other.latestInstalledVersion) return false
            if (declaredVersions != other.declaredVersions) return false
            if (searchableInfo != other.searchableInfo) return false

            return true
        }

        override fun hashCode(): Int {
            var result = usageInfo.hashCode()
            result = 31 * result + latestInstalledVersion.hashCode()
            result = 31 * result + declaredVersions.hashCode()
            result = 31 * result + searchableInfo.hashCode()
            return result
        }

        override fun toString(): String =
            "Installed(usageInfo=$usageInfo, latestInstalledVersion=$latestInstalledVersion, declaredVersions=$declaredVersions, " +
                "searchableInfo='$searchableInfo')"
    }

    class SearchResult(
        groupId: String,
        artifactId: String,
        remoteInfo: ApiStandardPackage,
        remoteVersions: List<NormalizedPackageVersion<PackageVersion.Named>>
    ) : PackageModel(groupId, artifactId, remoteInfo, remoteVersions) {

        companion object {

            suspend operator fun invoke(
                groupId: String,
                artifactId: String,
                remoteInfo: ApiStandardPackage,
                normalizer: PackageVersionNormalizer
            ) = SearchResult(groupId, artifactId, remoteInfo, evaluateRemoteVersions(remoteInfo, normalizer))
        }

        override val declaredVersions: List<NormalizedPackageVersion<*>> = emptyList()

        override val searchableInfo =
            buildString {
                appendLine(identifier)
                appendLine(remoteInfo.description)
                appendLine(remoteInfo.name)
            }.lowercase()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SearchResult) return false

            if (declaredVersions != other.declaredVersions) return false
            if (searchableInfo != other.searchableInfo) return false

            return true
        }

        override fun hashCode(): Int {
            var result = declaredVersions.hashCode()
            result = 31 * result + searchableInfo.hashCode()
            return result
        }

        override fun toString(): String = "SearchResult(declaredVersions=$declaredVersions, searchableInfo='$searchableInfo')"
    }

    companion object {

        suspend fun evaluateRemoteVersions(remoteInfo: ApiStandardPackage?, normalizer: PackageVersionNormalizer) =
            remoteInfo?.versions?.asFlow()
                ?.map { PackageVersion.from(it) }
                ?.filterIsInstance<PackageVersion.Named>()
                ?.map { normalizer.parse(it) }
                ?.toList()
                ?: emptyList()

        suspend fun fromSearchResult(remoteInfo: ApiStandardPackage, normalizer: PackageVersionNormalizer): SearchResult? {
            if (remoteInfo.versions.isEmpty()) return null

            return SearchResult(
                remoteInfo.groupId,
                remoteInfo.artifactId,
                remoteInfo,
                normalizer
            )
        }

        suspend fun fromInstalledDependency(
            unifiedDependency: UnifiedDependency,
            usageInfo: List<DependencyUsageInfo>,
            remoteInfo: ApiStandardPackage?,
            normalizer: PackageVersionNormalizer
        ): Installed? {
            val groupId = unifiedDependency.coordinates.groupId ?: return null
            val artifactId = unifiedDependency.coordinates.artifactId ?: return null

            if (usageInfo.isEmpty()) return null

            return Installed(groupId, artifactId, remoteInfo, usageInfo, normalizer)
        }
    }
}
