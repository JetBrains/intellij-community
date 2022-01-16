package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel

internal fun computePackagesTableItems(
    packages: List<UiPackageModel<*>>,
    targetModules: TargetModules,
): PackagesTable.ViewModel.TableItems {
    if (targetModules is TargetModules.None) {
        return PackagesTable.ViewModel.TableItems.EMPTY
    }

    val items = packages.map { uiPackageModel ->
        when (uiPackageModel) {
            is UiPackageModel.Installed -> {
                val scopes = (uiPackageModel.declaredScopes + uiPackageModel.userDefinedScopes)
                    .distinct()
                    .sorted()

                PackagesTableItem.InstalledPackage(uiPackageModel, scopes)
            }
            is UiPackageModel.SearchResult -> {
                val scopes = (uiPackageModel.declaredScopes + uiPackageModel.userDefinedScopes)
                    .distinct()
                    .sorted()

                PackagesTableItem.InstallablePackage(uiPackageModel, scopes)
            }
        }
    }
    return PackagesTable.ViewModel.TableItems(items)
}
