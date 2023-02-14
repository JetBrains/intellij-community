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

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * Provider interface for creating [ConfigurableContributorDriver]s.
 */
interface ConfigurableContributor {

    companion object {

        private val extensionPointName: ExtensionPointName<ConfigurableContributor> =
            ExtensionPointName.create("com.intellij.packagesearch.configurableContributor")

        /**
         * Returns all known [ConfigurableContributor] implementations.
         */
        fun extensionsForProject(project: Project): List<ConfigurableContributor> =
            extensionPointName.getExtensionList(project)
    }

    /**
     * Creates a [ConfigurableContributorDriver] that allows to modify the settings UI panel for Package Search.
     */
    fun createDriver(): ConfigurableContributorDriver
}
