package com.jetbrains.packagesearch.intellij.plugin.configuration

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.OptionTag

@State(
    name = "PackageSearchGeneralConfiguration",
    storages = [Storage(PackageSearchGeneralConfiguration.StorageFileName)]
)
class PackageSearchGeneralConfiguration : BaseState(), PersistentStateComponent<PackageSearchGeneralConfiguration> {

    companion object {

        const val StorageFileName = "packagesearch.xml"
        const val DefaultPackageDetailsSplitterProportion = 0.8f

        fun getInstance(project: Project): PackageSearchGeneralConfiguration =
            project.getService(PackageSearchGeneralConfiguration::class.java)
    }

    override fun getState(): PackageSearchGeneralConfiguration = this

    override fun loadState(state: PackageSearchGeneralConfiguration) {
        this.copyFrom(state)
    }

    @get:OptionTag("AUTO_SCROLL_TO_SOURCE")
    var autoScrollToSource by property(true)

    @get:OptionTag("AUTOMATICALLY_ADD_REPOSITORIES")
    var autoAddMissingRepositories by property(true)

    @get:OptionTag("PACKAGE_DETAILS_VISIBLE")
    var packageDetailsVisible by property(true)

    @get:OptionTag("PACKAGE_DETAILS_SPLITTER_PROPORTION")
    var packageDetailsSplitterProportion by property(DefaultPackageDetailsSplitterProportion)

}
