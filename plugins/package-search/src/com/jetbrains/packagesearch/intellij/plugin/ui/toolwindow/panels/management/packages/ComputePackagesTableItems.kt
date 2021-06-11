package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ModuleModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.SelectedPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug

internal fun computePackagesTableItems(
    project: Project,
    packages: List<PackageModel>,
    onlyStable: Boolean,
    targetModules: TargetModules,
    traceInfo: TraceInfo
): List<PackagesTableItem<*>> {
    logDebug(traceInfo, "PackagesTable#computeDisplayItems()") { "Creating item models for ${packages.size} item(s)" }

    if (targetModules is TargetModules.None) {
        logDebug(traceInfo, "PackagesTable#computeDisplayItems()") {
            "Current target modules is None, no items models to compute"
        }
        return emptyList()
    }
    logDebug(traceInfo, "PackagesTable#computeDisplayItems()") {
        "Current target modules value: ${targetModules.javaClass.simpleName} " +
            "${targetModules.modules.map { it.projectModule.name }}"
    }

    val modules = targetModules.modules

    val availableScopes = modules.flatMap { it.projectModule.moduleType.scopes(project) }
        .map { rawScope -> PackageScope.from(rawScope) }

    val mixedBuildSystems = targetModules.isMixedBuildSystems
    val defaultScope = if (!mixedBuildSystems) {
        PackageScope.from(modules.first().projectModule.moduleType.defaultScope(project))
    } else {
        PackageScope.Missing
    }

    return packages.map { packageModel ->
        when (packageModel) {
            is PackageModel.Installed -> {
                val installedScopes = packageModel.declaredScopes(modules)
                val selectedPackageModel = packageModel.toSelectedPackageModel(installedScopes, defaultScope, mixedBuildSystems)
                PackagesTableItem.InstalledPackage(selectedPackageModel, installedScopes, defaultScope)
            }
            is PackageModel.SearchResult -> {
                val selectedPackageModel = packageModel.toSelectedPackageModel(onlyStable, defaultScope, mixedBuildSystems)
                PackagesTableItem.InstallablePackage(selectedPackageModel, availableScopes, defaultScope)
            }
        }
    }
}

private fun PackageModel.Installed.declaredScopes(modules: List<ModuleModel>): List<PackageScope> =
    if (modules.isNotEmpty()) {
        findUsagesIn(modules).map { it.scope }
    } else {
        usageInfo.map { it.scope }
    }
        .distinct()
        .sorted()

private fun PackageModel.Installed.toSelectedPackageModel(
    installedScopes: List<PackageScope>,
    defaultScope: PackageScope,
    mixedBuildSystems: Boolean
): SelectedPackageModel<PackageModel.Installed> =
    SelectedPackageModel(
        packageModel = this,
        selectedVersion = getLatestInstalledVersion(),
        selectedScope = installedScopes.firstOrNull() ?: defaultScope,
        mixedBuildSystemTargets = mixedBuildSystems
    )

private fun PackageModel.SearchResult.toSelectedPackageModel(
    onlyStable: Boolean,
    defaultScope: PackageScope,
    mixedBuildSystems: Boolean
): SelectedPackageModel<PackageModel.SearchResult> =
    SelectedPackageModel(
        packageModel = this,
        selectedVersion = getLatestAvailableVersion(onlyStable) ?: PackageVersion.Missing,
        selectedScope = defaultScope,
        mixedBuildSystemTargets = mixedBuildSystems
    )
