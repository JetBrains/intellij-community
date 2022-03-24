package com.jetbrains.packagesearch.intellij.plugin.data

import com.jetbrains.packagesearch.api.v2.ApiRepository
import com.jetbrains.packagesearch.intellij.plugin.extensibility.RepositoryDeclaration
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ModuleModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.RepositoryModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.RepositoryUsageInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logInfo
import java.net.URI

internal fun allKnownRepositoryModels(
    allModules: List<ModuleModel>,
    knownRepositoriesRemoteInfo: List<ApiRepository>
) = KnownRepositories.All(
    knownRepositoriesRemoteInfo.map { remoteInfo ->
        val url = remoteInfo.url
        val id = remoteInfo.id

        RepositoryModel(
            id = id,
            name = remoteInfo.friendlyName,
            url = url,
            usageInfo = allModules.filter { module -> module.declaredRepositories.anyMatches(remoteInfo) }
                .map { module -> RepositoryUsageInfo(module.projectModule) },
            remoteInfo = remoteInfo
        )
    }
)

private fun List<RepositoryDeclaration>.anyMatches(remoteInfo: ApiRepository): Boolean {
    val urls = (remoteInfo.alternateUrls ?: emptyList()) + remoteInfo.url
    val id = remoteInfo.id
    if (urls.isEmpty() && id.isBlank()) return false
    return any { declaredRepo ->
        declaredRepo.id == id || urls.any { knownRepoUrl -> areEquivalentUrls(declaredRepo.url, knownRepoUrl) }
    }
}

private fun areEquivalentUrls(first: String?, second: String?): Boolean {
    if (first == null || second == null) return false
    val firstUri = tryParsingAsURI(first) ?: return false
    val secondUri = tryParsingAsURI(second) ?: return false
    return firstUri.normalize() == secondUri.normalize()
}

private fun tryParsingAsURI(rawValue: String): URI? =
    try {
        URI(rawValue.trim().trimEnd('/', '?', '#'))
    } catch (e: Exception) {
        logInfo("PackageSearchDataService#tryParsingAsURI") { "Unable to parse URI: '$rawValue'" }
        null
    }
