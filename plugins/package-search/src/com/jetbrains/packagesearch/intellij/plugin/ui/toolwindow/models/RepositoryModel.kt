package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.packagesearch.intellij.plugin.api.model.V2Repository
import com.jetbrains.packagesearch.intellij.plugin.extensibility.RepositoryDeclaration

internal data class RepositoryModel(
    val id: String?,
    val name: String?,
    val url: String?,
    val usageInfo: List<RepositoryUsageInfo>,
    val remoteInfo: V2Repository
) {

    @NlsSafe
    val displayName = remoteInfo.friendlyName
        ?: remoteInfo.url
        ?: remoteInfo.id

    fun isEquivalentTo(other: RepositoryDeclaration): Boolean {
        if (id != null && id == other.id) return true
        if (url != null && url == other.url) return true
        return false
    }
}
