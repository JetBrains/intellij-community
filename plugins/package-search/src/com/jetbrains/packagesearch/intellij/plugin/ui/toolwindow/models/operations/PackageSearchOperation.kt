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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion

internal sealed class PackageSearchOperation<T> {

    abstract val model: T
    abstract val packageSearchModule: PackageSearchModule

    sealed class Package : PackageSearchOperation<UnifiedDependency>() {

        abstract override val model: UnifiedDependency

        data class Remove(
            override val model: UnifiedDependency,
            override val packageSearchModule: PackageSearchModule,
            val currentVersion: PackageVersion,
            val currentScope: PackageScope
        ) : Package() {

            override fun toString() =
                "Package.Remove(model='${model.displayName}', moduleModule='${packageSearchModule.getFullName()}', " +
                    "currentVersion='$currentVersion', scope='$currentScope')"
        }
    }

    sealed class Repository : PackageSearchOperation<UnifiedDependencyRepository>() {

        abstract override val model: UnifiedDependencyRepository

        data class Remove(
            override val model: UnifiedDependencyRepository,
            override val packageSearchModule: PackageSearchModule
        ) : Repository() {

            override fun toString() = "Repository.Remove(model='${model.displayName}', moduleModule='${packageSearchModule.getFullName()}')"
        }
    }
}
