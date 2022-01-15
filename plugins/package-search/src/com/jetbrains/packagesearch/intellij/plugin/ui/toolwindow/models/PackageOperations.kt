package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageOperationType
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation

internal data class PackageOperations(
    val targetModules: TargetModules,
    val primaryOperations: List<PackageSearchOperation<*>>,
    val removeOperations: List<PackageSearchOperation<*>>,
    val targetVersion: PackageVersion?,
    val primaryOperationType: PackageOperationType?,
    val repoToAddWhenInstalling: RepositoryModel?
) {

    val canInstallPackage = primaryOperationType == PackageOperationType.INSTALL
    val canUpgradePackage = primaryOperationType == PackageOperationType.UPGRADE
    val canSetPackage = primaryOperationType == PackageOperationType.SET
    val canRemovePackage = removeOperations.isNotEmpty()
}
