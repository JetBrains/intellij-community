package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.api.model.V2Repository
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule

data class PackageSearchRepository(
    val id: String?,
    val name: String?,
    val url: String?,
    val projectModule: ProjectModule?,
    val remoteInfo: V2Repository?
)
