package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.jetbrains.packagesearch.api.v2.ApiStandardPackage
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.packageversionutils.PackageVersionUtils
import org.apache.commons.lang3.StringUtils

internal sealed class PackageModel(
    val groupId: String,
    val artifactId: String,
    val remoteInfo: ApiStandardPackage?
) : Comparable<PackageModel> {

    val identifier = PackageIdentifier("$groupId:$artifactId")

    val sortKey = (StringUtils.normalizeSpace(remoteInfo?.name) ?: identifier.rawValue.lowercase())

    val isKotlinMultiplatform = remoteInfo?.mpp != null

    fun getAvailableVersions(onlyStable: Boolean): List<PackageVersion> {
        val remoteVersions = remoteInfo?.versions
            ?.map { PackageVersion.from(it) }
            ?.filter { it != PackageVersion.Missing }

        val allVersions = additionalAvailableVersions()
            .union(remoteVersions ?: emptyList())
            .toList()

        return allVersions.asSequence()
            .filter { if (onlyStable) it.isStable else true }
            .distinctBy { it.versionName }
            .sortedDescending()
            .toList()
    }

    protected abstract fun additionalAvailableVersions(): List<PackageVersion>

    override fun compareTo(other: PackageModel): Int = sortKey.compareTo(other.sortKey)

    abstract val searchableInfo: String

    class Installed(
        groupId: String,
        artifactId: String,
        remoteInfo: ApiStandardPackage?,
        val usageInfo: List<DependencyUsageInfo>
    ) : PackageModel(groupId, artifactId, remoteInfo) {

        init {
            require(usageInfo.isNotEmpty()) { "An installed package must always have at least one usage" }
        }

        override fun additionalAvailableVersions(): List<PackageVersion> = usageInfo.map { it.version }

        fun findUsagesIn(moduleModels: List<ModuleModel>): List<DependencyUsageInfo> =
            findUsagesIn(moduleModels.map { it.projectModule })

        private fun findUsagesIn(projectModules: Collection<ProjectModule>): List<DependencyUsageInfo> {
            if (projectModules.isEmpty()) return emptyList()
            return usageInfo.filter { usageInfo -> projectModules.any { it == usageInfo.projectModule } }
        }

        fun copyWithUsages(usages: List<DependencyUsageInfo>) =
            Installed(groupId, artifactId, remoteInfo, usages)

        fun getLatestInstalledVersion(): PackageVersion = PackageVersionUtils.highestSensibleVersionByNameOrNull(
            usageInfo.asSequence()
                .map { it.version }
                .toList()
        )
            ?: usageInfo.maxByOrNull { it.version }?.version
            ?: error("An installed package must always have at least one usage")

        override val searchableInfo =
            buildString {
                appendLine(identifier)
                for (usage in usageInfo) {
                    appendLine(usage.version)
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
            if (searchableInfo != other.searchableInfo) return false

            return true
        }

        override fun hashCode(): Int {
            var result = usageInfo.hashCode()
            result = 31 * result + searchableInfo.hashCode()
            return result
        }

        override fun toString(): String = "Installed(usageInfo=$usageInfo, searchableInfo='$searchableInfo')"
    }

    class SearchResult(
        groupId: String,
        artifactId: String,
        remoteInfo: ApiStandardPackage
    ) : PackageModel(groupId, artifactId, remoteInfo) {

        override fun additionalAvailableVersions(): List<PackageVersion> = emptyList()

        override val searchableInfo =
            buildString {
                appendLine(identifier)
                appendLine(remoteInfo.description)
                appendLine(remoteInfo.name)
            }.lowercase()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SearchResult) return false

            if (searchableInfo != other.searchableInfo) return false

            return true
        }

        override fun hashCode(): Int = searchableInfo.hashCode()

        override fun toString(): String = "SearchResult(searchableInfo='$searchableInfo')"
    }

    companion object {

        fun fromSearchResult(remoteInfo: ApiStandardPackage): SearchResult? {
            if (remoteInfo.versions.isEmpty()) return null

            return SearchResult(
                remoteInfo.groupId,
                remoteInfo.artifactId,
                remoteInfo = remoteInfo
            )
        }

        fun fromInstalledDependency(
            unifiedDependency: UnifiedDependency,
            usageInfo: List<DependencyUsageInfo>,
            remoteInfo: ApiStandardPackage?
        ): Installed? {
            val groupId = unifiedDependency.coordinates.groupId ?: return null
            val artifactId = unifiedDependency.coordinates.artifactId ?: return null

            if (usageInfo.isEmpty()) return null

            return Installed(groupId, artifactId, remoteInfo, usageInfo)
        }
    }
}
