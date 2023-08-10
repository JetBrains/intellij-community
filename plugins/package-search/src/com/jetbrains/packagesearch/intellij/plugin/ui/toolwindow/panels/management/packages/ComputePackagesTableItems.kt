/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel

internal fun computePackagesTableItems(
    packages: List<UiPackageModel<*>>,
    targetModules: TargetModules,
): PackagesTable.ViewModel.TableItems {
    if (targetModules is TargetModules.None) return PackagesTable.ViewModel.TableItems.EMPTY
    val items = packages.map { uiPackageModel ->
        val scopes = (uiPackageModel.declaredScopes + uiPackageModel.userDefinedScopes)
            .distinct()
            .sorted()
        when (uiPackageModel) {
            is UiPackageModel.Installed -> PackagesTableItem.InstalledPackage(uiPackageModel, scopes)
            is UiPackageModel.SearchResult -> PackagesTableItem.InstallablePackage(uiPackageModel, scopes)
        }
    }
    return PackagesTable.ViewModel.TableItems(items)
}
