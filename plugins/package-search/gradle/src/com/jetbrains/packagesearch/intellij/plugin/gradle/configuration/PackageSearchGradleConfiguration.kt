/**
 * ****************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations
 * under the License.
 * ****************************************************************************
 */

package com.jetbrains.packagesearch.intellij.plugin.gradle.configuration

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.text.nullize
import com.intellij.util.xmlb.annotations.OptionTag
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration

@State(
    name = "PackageSearchGradleConfiguration",
    storages = [(Storage(PackageSearchGeneralConfiguration.StorageFileName))],
)
internal class PackageSearchGradleConfiguration : BaseState(), PersistentStateComponent<PackageSearchGradleConfiguration.State> {

    var defaultGradleScope = ""
    var gradleScopes = mutableSetOf<String>()
    var updateScopesOnUsage = true

    companion object {

        @JvmStatic
        fun getInstance(project: Project) = project.service<PackageSearchGradleConfiguration>()
    }

    override fun getState(): State = State().also {
        it.defaultGradleScope = defaultGradleScope
        it.gradleScopes = gradleScopes
        it.updateScopesOnUsage = updateScopesOnUsage
    }

    @Suppress("DEPRECATION") // Used for migration only
    override fun loadState(state: State) {
        defaultGradleScope = state.defaultGradleScope.nullize(true)
            ?: PackageSearchGradleConfigurationDefaults.GradleDefaultScope

        gradleScopes = state.gradleScopes
            .ifEmpty { state.getLegacyScopes() }
            .ifEmpty { PackageSearchGradleConfigurationDefaults.GradleScopes }
            .toMutableSet()

        state.legacyGradleScopes = null

        updateScopesOnUsage = state.updateScopesOnUsage
    }

    class State : BaseState() {

        @Deprecated("Use gradleScopes instead", ReplaceWith("gradleScopes"))
        @get:OptionTag("GRADLE_SCOPES")
        var legacyGradleScopes by string(PackageSearchGradleConfigurationDefaults.LegacyGradleScopes)

        @get:OptionTag("GRADLE_SCOPES_V2")
        var gradleScopes by stringSet()

        @get:OptionTag("GRADLE_SCOPES_DEFAULT")
        var defaultGradleScope by string(PackageSearchGradleConfigurationDefaults.GradleDefaultScope)

        @get:OptionTag("UPDATE_SCOPES_ON_USE")
        var updateScopesOnUsage by property(true)

        @Deprecated("Only to be used for migration from legacy scopes")
        internal fun getLegacyScopes(): Set<String> =
            legacyGradleScopes.orEmpty()
                .split(",", ";", "\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
    }

    fun addGradleScope(scope: String) {
        val previousSize = gradleScopes.size
        gradleScopes += scope
        if (previousSize != gradleScopes.size) incrementModificationCount()
    }
}
