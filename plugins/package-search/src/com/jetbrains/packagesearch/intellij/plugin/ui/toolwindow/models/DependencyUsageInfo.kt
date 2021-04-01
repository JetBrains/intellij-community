package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule

internal data class DependencyUsageInfo(
    val projectModule: ProjectModule,
    val version: PackageVersion,
    val scope: PackageScope,
    val availableScopes: List<PackageScope>
)
