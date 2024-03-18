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

package com.jetbrains.packagesearch.intellij.plugin.configuration

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.OptionTag

@Service(Service.Level.PROJECT)
@State(
    name = "PackageSearchGeneralConfiguration",
    storages = [Storage(PackageSearchGeneralConfiguration.StorageFileName)]
)
class PackageSearchGeneralConfiguration : BaseState(), PersistentStateComponent<PackageSearchGeneralConfiguration> {

    companion object {

        const val StorageFileName = "packagesearch.xml"
        const val DefaultPackageDetailsSplitterProportion = 0.8f

        fun getInstance(project: Project): PackageSearchGeneralConfiguration =
            project.service()
    }

    override fun getState(): PackageSearchGeneralConfiguration = this

    override fun loadState(state: PackageSearchGeneralConfiguration) = copyFrom(state)

    @get:OptionTag("AUTO_SCROLL_TO_SOURCE")
    var autoScrollToSource by property(true)

    @get:OptionTag("AUTOMATICALLY_ADD_REPOSITORIES")
    var autoAddMissingRepositories by property(true)

    @get:OptionTag("PACKAGE_DETAILS_VISIBLE")
    var packageDetailsVisible by property(true)

    @get:OptionTag("PACKAGE_DETAILS_SPLITTER_PROPORTION")
    var packageDetailsSplitterProportion by property(DefaultPackageDetailsSplitterProportion)
}
