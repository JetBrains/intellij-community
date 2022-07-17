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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesHeaderData
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo

internal data class RootDataModel(
    val moduleModels: List<ModuleModel>,
    val packageModels: List<UiPackageModel<*>>,
    val packagesToUpdate: PackagesToUpgrade,
    val headerData: PackagesHeaderData,
    val targetModules: TargetModules,
    val allKnownRepositories: KnownRepositories.All,
    val knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
    val selectedPackage: UiPackageModel<*>?,
    val filterOptions: FilterOptions,
    val traceInfo: TraceInfo,
    val searchQuery: String
) {

//    companion object {
//
//        val EMPTY = RootDataModel(
//            moduleModels = emptyList(),
//            packageModels = emptyList(),
//            packagesToUpdate = PackagesToUpgrade.EMPTY,
//            headerData = PackagesHeaderData.EMPTY,
//            targetModules = TargetModules.None,
//            allKnownRepositories = KnownRepositories.All.EMPTY,
//            knownRepositoriesInTargetModules = KnownRepositories.InTargetModules.EMPTY,
//            selectedPackage = null,
//            filterOptions = FilterOptions(),
//            traceInfo = TraceInfo.EMPTY,
//            searchQuery = ""
//        )
//    }
}
