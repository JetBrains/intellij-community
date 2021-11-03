package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageOperationType
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import kotlinx.coroutines.Deferred

internal data class PackageOperations(
    val targetModules: TargetModules,
    val primaryOperations: Deferred<List<PackageSearchOperation<*>>>,
    val removeOperations: Deferred<List<PackageSearchOperation<*>>>,
    val targetVersion: NormalizedPackageVersion<*>?,
    val primaryOperationType: PackageOperationType?,
    val repoToAddWhenInstalling: RepositoryModel?
) {

    val canInstallPackage = primaryOperationType == PackageOperationType.INSTALL
    val canUpgradePackage = primaryOperationType == PackageOperationType.UPGRADE
    val canSetPackage = primaryOperationType == PackageOperationType.SET
}
