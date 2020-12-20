package com.jetbrains.packagesearch.intellij.plugin.extensions.gradle.mpp.configuration

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.OptionTag
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration

fun packageSearchGradleMppConfigurationForProject(project: Project): PackageSearchGradleMppConfiguration =
    ServiceManager.getService(project, PackageSearchGradleMppConfiguration::class.java)

@State(
    name = "PackageSearchGradleMppConfiguration",
    storages = [(Storage(PackageSearchGeneralConfiguration.StorageFileName))]
)
class PackageSearchGradleMppConfiguration : BaseState(), PersistentStateComponent<PackageSearchGradleMppConfiguration> {

    override fun getState(): PackageSearchGradleMppConfiguration? = this

    override fun loadState(state: PackageSearchGradleMppConfiguration) {
        this.copyFrom(state)
    }

    @get:OptionTag("GRADLE_MPP_SCOPES")
    var gradleScopes by string(PackageSearchGradleMppConfigurationDefaults.GradleScopes)

    @get:OptionTag("GRADLE_MPP_SCOPES_DEFAULT")
    var defaultGradleScope by string(PackageSearchGradleMppConfigurationDefaults.GradleScope)

    fun determineDefaultGradleScope(): String =
        if (!defaultGradleScope.isNullOrEmpty()) {
            defaultGradleScope!!
        } else {
            PackageSearchGradleMppConfigurationDefaults.GradleScope
        }

    fun getGradleScopes(): List<String> {
        var scopes = gradleScopes.orEmpty().split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (scopes.isEmpty()) {
            scopes = PackageSearchGradleMppConfigurationDefaults.GradleScopes.split(",", ";", "\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        return scopes
    }
}
