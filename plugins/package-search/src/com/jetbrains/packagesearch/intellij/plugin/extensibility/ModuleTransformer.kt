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

package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.extensions.AreaInstance
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.util.asCoroutine
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval

/**
 * Extension point used to register [Module]s transformations to [ProjectModule]s.
 */
@Deprecated(
    "Use the async version instead",
    ReplaceWith("AsyncModuleTransformer"),
    DeprecationLevel.WARNING
)
@ScheduledForRemoval
interface ModuleTransformer {

    companion object {

        private val extensionPointName: ExtensionPointName<ModuleTransformer> =
            ExtensionPointName.create("com.intellij.packagesearch.moduleTransformer")

        internal fun extensions(areaInstance: AreaInstance) =
            extensionPointName.getExtensionList(areaInstance).asSequence().map { it.asCoroutine() }.toList()
    }

    /**
     * IMPORTANT: This function is NOT invoked inside a read action.
     *
     * Transforms [nativeModules] in a [ProjectModule] module if possible, else returns an empty list.
     * Its implementation should use the IntelliJ platform APIs for a given build system (eg.
     * Gradle or Maven), detect if and which [nativeModules] are controlled by said build system
     * and transform them accordingly.
     *
     * NOTE: some [Module]s in [nativeModules] may be already disposed or about to be. Be sure to
     * handle any exceptions and filter out the ones not working.
     *
     * @param nativeModules The native [Module]s that will be transformed.
     * @return [ProjectModule]s wrapping [nativeModules] or an empty list.
     */
    fun transformModules(project: Project, nativeModules: List<Module>): List<ProjectModule>
}
