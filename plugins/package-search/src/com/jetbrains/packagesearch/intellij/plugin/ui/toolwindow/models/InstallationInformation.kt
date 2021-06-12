package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import org.jetbrains.annotations.NonNls

internal data class InstallationInformation(val projectModule: ProjectModule, val installedVersion: String, val rawScope: String?) {

    @NonNls
    val scope = rawScope ?: DEFAULT_SCOPE

    companion object {

        @NonNls
        val DEFAULT_SCOPE = PackageSearchBundle.message("packagesearch.ui.missingScope")
    }
}
