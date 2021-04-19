package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Package
import org.apache.commons.lang3.StringUtils
import java.util.Locale

internal sealed class PackageModel(
    val groupId: String,
    val artifactId: String,
    val remoteInfo: StandardV2Package?
) : Comparable<PackageModel> {

    val identifier = "$groupId:$artifactId".toLowerCase(Locale.ROOT)

    val sortKey = (StringUtils.normalizeSpace(remoteInfo?.name) ?: identifier).toLowerCase(Locale.ROOT)

    val isKotlinMultiplatform = remoteInfo?.mpp != null

    private val latestAvailableVersion by lazy { getAvailableVersions(onlyStable = false).firstOrNull() }

    private val latestAvailableStableVersion by lazy { getAvailableVersions(onlyStable = true).firstOrNull() }

    fun getLatestAvailableVersion(onlyStable: Boolean): PackageVersion? =
        if (onlyStable) latestAvailableStableVersion else latestAvailableVersion

    fun getAvailableVersions(onlyStable: Boolean): List<PackageVersion> {
        val remoteVersions = remoteInfo?.versions
            ?.map { PackageVersion.from(it) }
            ?.filter { it != PackageVersion.Missing }

        val allVersions = additionalAvailableVersions()
            .union(remoteVersions ?: emptyList())

        return allVersions.filter { if (onlyStable) it.isStable else true }
            .distinctBy { it.versionName }
            .sortedDescending()
    }

    protected abstract fun additionalAvailableVersions(): List<PackageVersion>

    private val sortingKey: String = remoteInfo?.name ?: identifier

    override fun compareTo(other: PackageModel): Int = sortingKey.compareTo(other.sortingKey)

    abstract val searchableInfo: String

    class Installed(
        groupId: String,
        artifactId: String,
        remoteInfo: StandardV2Package?,
        val usageInfo: List<DependencyUsageInfo>
    ) : PackageModel(groupId, artifactId, remoteInfo) {

        init {
            require(usageInfo.isNotEmpty()) { "An installed package must always have at least one usage" }
        }

        override fun additionalAvailableVersions(): List<PackageVersion> = usageInfo.map { it.version }

        fun findUsagesIn(modules: List<ModuleModel>): List<DependencyUsageInfo> {
            if (modules.isEmpty()) return emptyList()
            return usageInfo.filter { usageInfo -> modules.any { it.projectModule == usageInfo.projectModule } }
        }

        fun canBeUpgraded(onlyStable: Boolean): Boolean {
            val latestVersion = getLatestAvailableVersion(onlyStable) ?: return false
            return usageInfo.any { it.version < latestVersion }
        }

        fun canBeUpgraded(currentVersion: PackageVersion, onlyStable: Boolean): Boolean {
            val latestVersion = getLatestAvailableVersion(onlyStable) ?: return false
            return currentVersion < latestVersion
        }

        fun canBeDowngraded(currentVersion: PackageVersion, onlyStable: Boolean): Boolean {
            val latestVersion = getLatestAvailableVersion(onlyStable) ?: return false
            return currentVersion > latestVersion
        }

        fun getLatestInstalledVersion(): PackageVersion = usageInfo.maxByOrNull { it.version }?.version
            ?: throw IllegalStateException("An installed package must always have at least one usage")

        override val searchableInfo = buildString {
            appendLine(identifier)
            for (usage in usageInfo) {
                appendLine(usage.version)
            }

            if (remoteInfo != null) {
                appendLine(remoteInfo.description)
                appendLine(remoteInfo.name)
            }
        }.toLowerCase(Locale.ROOT)
    }

    class SearchResult(
        groupId: String,
        artifactId: String,
        remoteInfo: StandardV2Package
    ) : PackageModel(groupId, artifactId, remoteInfo) {

        override fun additionalAvailableVersions(): List<PackageVersion> = emptyList()

        override val searchableInfo = buildString {
            appendLine(identifier)
            appendLine(remoteInfo.description)
            appendLine(remoteInfo.name)
        }.toLowerCase(Locale.ROOT)
    }

    companion object {

        fun fromSearchResult(remoteInfo: StandardV2Package): SearchResult? {
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
            remoteInfo: StandardV2Package?
        ): Installed? {
            val groupId = unifiedDependency.coordinates.groupId ?: return null
            val artifactId = unifiedDependency.coordinates.artifactId ?: return null

            if (usageInfo.isEmpty()) return null

            return Installed(groupId, artifactId, remoteInfo, usageInfo)
        }
    }
}
