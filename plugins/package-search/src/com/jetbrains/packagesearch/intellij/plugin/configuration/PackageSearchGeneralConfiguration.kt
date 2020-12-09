package com.jetbrains.packagesearch.intellij.plugin.configuration

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.OptionTag

@State(
    name = "PackageSearchGeneralConfiguration",
    storages = [(Storage(PackageSearchGeneralConfiguration.StorageFileName))]
)
class PackageSearchGeneralConfiguration : BaseState(), PersistentStateComponent<PackageSearchGeneralConfiguration> {

    companion object {
        const val StorageFileName = "packagesearch.xml"

        fun getInstance(project: Project): PackageSearchGeneralConfiguration =
            ServiceManager.getService(project, PackageSearchGeneralConfiguration::class.java)
    }

    override fun getState(): PackageSearchGeneralConfiguration? = this

    override fun loadState(state: PackageSearchGeneralConfiguration) {
        this.copyFrom(state)
    }

    @get:OptionTag("REFRESH_PROJECT")
    var refreshProject by property(true)

    @get:OptionTag("ALLOW_CHECK_FOR_PACKAGE_UPGRADES")
    var allowCheckForPackageUpgrades by property(true)

    @get:OptionTag("AUTO_SCROLL_TO_SOURCE")
    var autoScrollToSource by property(true)
}
