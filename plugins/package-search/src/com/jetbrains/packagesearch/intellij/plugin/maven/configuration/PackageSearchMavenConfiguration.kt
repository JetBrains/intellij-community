package com.jetbrains.packagesearch.intellij.plugin.maven.configuration

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.OptionTag
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration

@State(
    name = "PackageSearchMavenConfiguration",
    storages = [(Storage(PackageSearchGeneralConfiguration.StorageFileName))],
)
internal class PackageSearchMavenConfiguration : BaseState(), PersistentStateComponent<PackageSearchMavenConfiguration> {

    companion object {

        const val DEFAULT_MAVEN_SCOPE = "compile"

        @JvmStatic
        fun getInstance(project: Project) = project.service<PackageSearchMavenConfiguration>()
    }

    override fun getState(): PackageSearchMavenConfiguration = this

    override fun loadState(state: PackageSearchMavenConfiguration) {
        this.copyFrom(state)
    }

    @get:OptionTag("MAVEN_SCOPES_DEFAULT")
    var defaultMavenScope by string(DEFAULT_MAVEN_SCOPE)

    fun determineDefaultMavenScope() = if (!defaultMavenScope.isNullOrEmpty()) defaultMavenScope!! else DEFAULT_MAVEN_SCOPE

    fun getMavenScopes() = listOf(DEFAULT_MAVEN_SCOPE, "provided", "runtime", "test", "system", "import")
}
