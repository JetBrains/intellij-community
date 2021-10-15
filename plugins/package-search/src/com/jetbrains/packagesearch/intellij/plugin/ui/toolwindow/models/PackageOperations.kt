package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageOperationType
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion

internal data class PackageOperations(
    val targetModules: TargetModules,
    val primaryOperations: List<PackageSearchOperation<*>>,
    val removeOperations: List<PackageSearchOperation<*>>,
    val targetVersion: NormalizedPackageVersion<*>?,
    val primaryOperationType: PackageOperationType?,
    val repoToAddWhenInstalling: RepositoryModel?
) {

    val canInstallPackage = primaryOperationType == PackageOperationType.INSTALL
    val canUpgradePackage = primaryOperationType == PackageOperationType.UPGRADE
    val canSetPackage = primaryOperationType == PackageOperationType.SET
    val canRemovePackage = removeOperations.isNotEmpty()
}
