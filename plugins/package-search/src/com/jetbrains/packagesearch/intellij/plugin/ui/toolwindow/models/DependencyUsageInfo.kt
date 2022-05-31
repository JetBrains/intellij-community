package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion.Missing

internal data class DependencyUsageInfo(
    val projectModule: ProjectModule,
    val declaredVersion: PackageVersion,
    val resolvedVersion: PackageVersion,
    val scope: PackageScope,
    val userDefinedScopes: List<PackageScope>,
    val declarationIndexInBuildFile: DependencyDeclarationIndexes?
) {

    fun getResolvedVersionOrFallback() = if (resolvedVersion !is Missing) resolvedVersion else declaredVersion
}
