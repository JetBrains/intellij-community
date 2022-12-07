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

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule

sealed class TargetModules {

    abstract val modules: List<PackageSearchModule>
    abstract val isMixedBuildSystems: Boolean

    val id by lazy { modules.map { it.projectDir.absolutePath }.hashCode() }

    fun declaredScopes(project: Project): List<PackageScope> =
        modules.flatMap { it.moduleType.userDefinedScopes(project) }
            .distinct()
            .sorted()

    fun defaultScope(project: Project): PackageScope =
        if (!isMixedBuildSystems && this !is None) {
            modules.first().moduleType.defaultScope(project)
        } else {
            PackageScope.Missing
        }

    object None : TargetModules() {

        override val modules: List<PackageSearchModule>
            get() = emptyList()
        override val isMixedBuildSystems: Boolean
            get() = false

        override fun toString() = "None(modules=[], isMixedBuildSystems=false)"
    }

    data class One(val module: PackageSearchModule) : TargetModules() {

        override val modules
            get() = listOf(module)
        override val isMixedBuildSystems: Boolean
            get() = false

        override fun toString() = "One(modules=[${module.name}], isMixedBuildSystems=false)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is One) return false

            if (module != other.module) return false

            return true
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + module.hashCode()
            return result
        }
    }

    class All(
        override val modules: List<PackageSearchModule>,
        override val isMixedBuildSystems: Boolean
    ) : TargetModules() {

        init {
            require(modules.isNotEmpty()) { "There must be at least one module in an All target" }
        }

        override fun toString() = "All(${modules.size} module(s), isMixedBuildSystems=$isMixedBuildSystems)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is All) return false

            if (modules.any { module -> other.modules.none { otherModule -> module == otherModule } }) return false
            if (isMixedBuildSystems != other.isMixedBuildSystems) return false

            return true
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + modules.hashCode()
            result = 31 * result + isMixedBuildSystems.hashCode()
            return result
        }
    }

    companion object {

        fun from(selectedModule: PackageSearchModule?) = if (selectedModule != null) {
            One(selectedModule)
        } else {
            None
        }

        fun all(allModules: List<PackageSearchModule>) = All(
            allModules,
            allModules.asSequence()
                .map { it.buildSystemType }
                .distinct()
                .count() > 1
        )
    }
}
