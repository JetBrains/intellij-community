package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule

data class InstallationInformation(val projectModule: ProjectModule, val installedVersion: String, val rawScope: String?) {

    val scope = rawScope ?: DEFAULT_SCOPE

    companion object {
        const val DEFAULT_SCOPE = "(default)" // Non-nls?
    }
}
