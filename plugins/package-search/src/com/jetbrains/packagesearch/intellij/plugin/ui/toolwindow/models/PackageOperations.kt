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
