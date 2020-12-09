package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.util.text.VersionComparatorUtil
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Package
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Version
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.looksLikeGradleVariable
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InfoLink.CODE_OF_CONDUCT
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InfoLink.DOCUMENTATION
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InfoLink.PROJECT_SITE
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InfoLink.README
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.right.extractScmUrl
import com.jetbrains.packagesearch.intellij.plugin.version.looksLikeStableVersion

data class PackageSearchDependency(
    val groupId: String,
    val artifactId: String,
    val installationInformation: MutableList<InstallationInformation> = mutableListOf(),
    var remoteInfo: StandardV2Package? = null
) {

    val identifier = "$groupId:$artifactId".toLowerCase()

    val isInstalled: Boolean
        get() = installationInformation.isNotEmpty()

    fun getLowestInstalledVersion(projectModules: List<ProjectModule>? = null, repositoryIds: List<String>) =
        installationInformation.asSequence()
            .filter { projectModules == null || projectModules.isEmpty() || projectModules.contains(it.projectModule) }
            .map { it.installedVersion }
            .filter { it.isNotBlank() && !looksLikeGradleVariable(it) }
            .filter {
                val remoteVersions = remoteInfo?.versions
                remoteVersions == null || remoteVersions.any { version -> it == version.version && version.matchesRepositoryIds(repositoryIds) } }
            .sortedWith(Comparator { o1, o2 ->
                VersionComparatorUtil.compare(o1, o2) // older versions on top
            })
            .firstOrNull()

    fun getAvailableVersions(onlyStable: Boolean, repositoryIds: List<String>) =
        installationInformation.map { it.installedVersion }
            .union(remoteInfo?.versions
                ?.filter { it.matchesRepositoryIds(repositoryIds) }
                ?.map { it.version } ?: emptyList())
            .filter { it.isNotBlank() && !looksLikeGradleVariable(it) && (!onlyStable || looksLikeStableVersion(it)) }
            .distinct()
            .sortedWith(Comparator { o1, o2 ->
                VersionComparatorUtil.compare(o2, o1) // latest versions on top
            })

    fun getLatestAvailableVersion(onlyStable: Boolean, repositoryIds: List<String>) =
        getAvailableVersions(onlyStable, repositoryIds).firstOrNull()

    fun buildInstallationSummary(): String = installationInformation.joinToString("\n") {
        it.projectModule.getFullName() + " - " + identifier + ":" + it.installedVersion + ":" + it.rawScope
    }

    fun getAllLinks(): MutableMap<InfoLink, String> {
        val links = mutableMapOf<InfoLink, String>()
        remoteInfo?.url?.let {
            if (it.isNotEmpty()) links[PROJECT_SITE] = it
        }
        extractScmUrl(remoteInfo?.scm)?.let { scmUrl ->
            if (scmUrl.url.startsWith("http", ignoreCase = true)) {
                // Only display HTTP(s) links
                links[scmUrl.type.linkKey] = scmUrl.url
            }
        }
        remoteInfo?.gitHub?.communityProfile?.let {
            if (!it.documentationUrl.isNullOrEmpty()) links[DOCUMENTATION] = it.documentationUrl
            it.files?.readme?.let { gitHubFile ->
                val url = gitHubFile.htmlUrl ?: gitHubFile.url
                if (!url.isNullOrEmpty()) links[README] = url
            }
            it.files?.codeOfConduct?.let { gitHubFile ->
                val url = gitHubFile.htmlUrl ?: gitHubFile.url
                if (!url.isNullOrEmpty()) links[CODE_OF_CONDUCT] = url
            }
        }
        return links
    }
}

private fun StandardV2Version.matchesRepositoryIds(repositoryIds: List<String>) =
    repositoryIds.isEmpty() ||
        this.repositoryIds.isNullOrEmpty() ||
        this.repositoryIds.any { repo -> repositoryIds.contains(repo) }
