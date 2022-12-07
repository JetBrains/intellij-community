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

import com.intellij.openapi.module.Module
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import com.jetbrains.packagesearch.intellij.plugin.normalizeWhitespace
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import org.jetbrains.packagesearch.api.v2.ApiStandardPackage

internal sealed class PackageModel : Comparable<PackageModel> {

    abstract val groupId: String
    abstract val artifactId: String
    abstract val remoteInfo: ApiStandardPackage?
    abstract val remoteVersions: List<NormalizedPackageVersion<PackageVersion.Named>>

    val identifier
        get() = PackageIdentifier("$groupId:$artifactId")

    val sortKey
        get() = (remoteInfo?.name.normalizeWhitespace() ?: identifier.rawValue.lowercase())

    val isKotlinMultiplatform
        get() = remoteInfo?.mpp != null

    fun getAvailableVersions(onlyStable: Boolean): List<NormalizedPackageVersion<*>> {

        return declaredVersions.union(remoteVersions)
            .asSequence()
            .filter { if (onlyStable) it.isStable else true }
            .distinctBy { it.versionName }
            .sortedDescending()
            .toList()
    }

    abstract val declaredVersions: List<NormalizedPackageVersion<*>>

    override fun compareTo(other: PackageModel): Int = sortKey.compareTo(other.sortKey)

    abstract val searchableInfo: String

    class Installed(
        override val groupId: String,
        override val artifactId: String,
        override val remoteInfo: ApiStandardPackage?,
        override val remoteVersions: List<NormalizedPackageVersion<PackageVersion.Named>>,
        val usagesByModule: Map<Module, List<DependencyUsageInfo>>,
        val latestInstalledVersion: NormalizedPackageVersion<*>,
        val highestStableVersion: NormalizedPackageVersion<*>,
        val highestUnstableVersion: NormalizedPackageVersion<*>?,
        override val declaredVersions: List<NormalizedPackageVersion<*>>
    ) : PackageModel() {

        init {
            require(usagesByModule.isNotEmpty()) { "An installed package must always have at least one usage" }
        }

        fun getHighestVersion(onlyStable: Boolean) = when {
            onlyStable || highestUnstableVersion == null -> highestStableVersion
            else -> highestUnstableVersion
        }

        fun findUsagesIn(moduleModels: List<PackageSearchModule>): List<DependencyUsageInfo> =
            moduleModels.flatMap { usagesByModule[it] ?: emptyList() }

        override val searchableInfo =
            buildString {
                appendLine(identifier)
                for (usage in usagesByModule.flatMap { (_, v) -> v }) {
                    appendLine(usage.declaredVersion)
                }

                if (remoteInfo != null) {
                    appendLine(remoteInfo.description)
                    appendLine(remoteInfo.name)
                }
            }.lowercase()

        override fun toString(): String =
            "PackageModel.Installed(groupId=$groupId, artifactId=$artifactId)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Installed

            if (usagesByModule != other.usagesByModule) return false
            if (latestInstalledVersion != other.latestInstalledVersion) return false
            if (highestStableVersion != other.highestStableVersion) return false
            if (highestUnstableVersion != other.highestUnstableVersion) return false
            if (declaredVersions != other.declaredVersions) return false
            if (searchableInfo != other.searchableInfo) return false

            return true
        }

        override fun hashCode(): Int {
            var result = usagesByModule.hashCode()
            result = 31 * result + latestInstalledVersion.hashCode()
            result = 31 * result + highestStableVersion.hashCode()
            result = 31 * result + (highestUnstableVersion?.hashCode() ?: 0)
            result = 31 * result + declaredVersions.hashCode()
            result = 31 * result + searchableInfo.hashCode()
            return result
        }
    }

    class SearchResult(
        override val groupId: String,
        override val artifactId: String,
        override val remoteInfo: ApiStandardPackage,
        override val remoteVersions: List<NormalizedPackageVersion<PackageVersion.Named>>
    ) : PackageModel() {

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

}

internal data class InstalledDependenciesUsages(
    val all: List<PackageModel.Installed>,
    val byModule: Map<Module, List<PackageModel.Installed>>
) {

    companion object {
        internal val EMPTY: InstalledDependenciesUsages
            get() = InstalledDependenciesUsages(emptyList(), emptyMap())
    }
}

internal operator fun <V> Map<Module, V>.get(key: PackageSearchModule) = get(key.nativeModule)
