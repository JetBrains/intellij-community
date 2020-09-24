package com.jetbrains.packagesearch.intellij.plugin.extensions.maven.configuration

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.OptionTag
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration

fun packageSearchMavenConfigurationForProject(project: Project): PackageSearchMavenConfiguration =
    ServiceManager.getService(project, PackageSearchMavenConfiguration::class.java)

@State(
    name = "PackageSearchMavenConfiguration",
    storages = [(Storage(PackageSearchGeneralConfiguration.StorageFileName))]
)
class PackageSearchMavenConfiguration : BaseState(), PersistentStateComponent<PackageSearchMavenConfiguration> {

    override fun getState(): PackageSearchMavenConfiguration? = this

    override fun loadState(state: PackageSearchMavenConfiguration) {
        this.copyFrom(state)
    }

    @get:OptionTag("MAVEN_SCOPES_DEFAULT")
    var defaultMavenScope by string(PackageSearchMavenConfigurationDefaults.MavenScope)

    fun determineDefaultMavenScope(): String =
        if (!defaultMavenScope.isNullOrEmpty()) {
            defaultMavenScope!!
        } else {
            PackageSearchMavenConfigurationDefaults.MavenScope
        }

    fun getMavenScopes(): List<String> = PackageSearchMavenConfigurationDefaults.MavenScopes
        .split(",", ";", "\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}
