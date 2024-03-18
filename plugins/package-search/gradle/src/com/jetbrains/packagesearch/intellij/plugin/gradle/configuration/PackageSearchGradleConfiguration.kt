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

package com.jetbrains.packagesearch.intellij.plugin.gradle.configuration

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.OptionTag
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration

@Service(Service.Level.PROJECT)
@State(
    name = "PackageSearchGradleConfiguration",
    storages = [(Storage(PackageSearchGeneralConfiguration.StorageFileName))],
)
internal class PackageSearchGradleConfiguration : BaseState(), PersistentStateComponent<PackageSearchGradleConfiguration> {

    companion object {

        @JvmStatic
        fun getInstance(project: Project) = project.service<PackageSearchGradleConfiguration>()
    }

    override fun getState(): PackageSearchGradleConfiguration = this

    override fun loadState(state: PackageSearchGradleConfiguration) {
        this.copyFrom(state)
    }

    @get:OptionTag("GRADLE_SCOPES")
    var gradleScopes by string(PackageSearchGradleConfigurationDefaults.GradleScopes)

    @get:OptionTag("GRADLE_SCOPES_DEFAULT")
    var defaultGradleScope by string(PackageSearchGradleConfigurationDefaults.GradleDefaultScope)

    @get:OptionTag("UPDATE_SCOPES_ON_USE")
    var updateScopesOnUsage by property(true)

    fun determineDefaultGradleScope(): String =
        if (!defaultGradleScope.isNullOrEmpty()) {
            defaultGradleScope!!
        } else {
            PackageSearchGradleConfigurationDefaults.GradleDefaultScope
        }

    fun addGradleScope(scope: String) {
        val currentScopes = getGradleScopes()
        if (!currentScopes.contains(scope)) {
            gradleScopes = currentScopes.joinToString(",") + ",$scope"
            this.incrementModificationCount()
        }
    }

    fun getGradleScopes(): List<String> {
        var scopes = gradleScopes.orEmpty().split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (scopes.isEmpty()) {
            scopes = PackageSearchGradleConfigurationDefaults.GradleScopes.split(",", ";", "\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        return scopes
    }
}
