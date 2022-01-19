package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel
import kotlin.time.Duration
import kotlin.time.measureTimedValue

internal fun computePackagesTableItems(
    packages: List<UiPackageModel<*>>,
    targetModules: TargetModules,
    onComplete: (Duration) -> Unit = {}
): PackagesTable.ViewModel.TableItems {
    val (result, time) = measureTimedValue {
        if (targetModules is TargetModules.None) {
            return@measureTimedValue PackagesTable.ViewModel.TableItems.EMPTY
        }

        val items = packages.map { uiPackageModel ->
            val scopes = (uiPackageModel.declaredScopes + uiPackageModel.userDefinedScopes)
                .distinct()
                .sorted()
            when (uiPackageModel) {
                is UiPackageModel.Installed -> PackagesTableItem.InstalledPackage(uiPackageModel, scopes)
                is UiPackageModel.SearchResult -> PackagesTableItem.InstallablePackage(uiPackageModel, scopes)
            }
        }
        PackagesTable.ViewModel.TableItems(items)
    }
    onComplete(time)
    return result
}
