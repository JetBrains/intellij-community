package com.jetbrains.packagesearch.intellij.plugin.extensions.gradle.configuration

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.OptionTag
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration

fun packageSearchGradleConfigurationForProject(project: Project): PackageSearchGradleConfiguration =
    ServiceManager.getService(project, PackageSearchGradleConfiguration::class.java)

@State(
    name = "PackageSearchGradleConfiguration",
    storages = [(Storage(PackageSearchGeneralConfiguration.StorageFileName))]
)
class PackageSearchGradleConfiguration : BaseState(), PersistentStateComponent<PackageSearchGradleConfiguration> {

    override fun getState(): PackageSearchGradleConfiguration? = this

    override fun loadState(state: PackageSearchGradleConfiguration) {
        this.copyFrom(state)
    }

    @get:OptionTag("GRADLE_SCOPES")
    var gradleScopes by string(PackageSearchGradleConfigurationDefaults.GradleScopes)

    @get:OptionTag("GRADLE_SCOPES_DEFAULT")
    var defaultGradleScope by string(PackageSearchGradleConfigurationDefaults.GradleScope)

    @get:OptionTag("UPDATE_SCOPES_ON_USE")
    var updateScopesOnUsage by property(true)

    fun determineDefaultGradleScope(): String =
        if (!defaultGradleScope.isNullOrEmpty()) {
            defaultGradleScope!!
        } else {
            PackageSearchGradleConfigurationDefaults.GradleScope
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
