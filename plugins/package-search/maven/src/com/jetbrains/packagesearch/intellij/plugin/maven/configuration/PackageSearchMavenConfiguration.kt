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

package com.jetbrains.packagesearch.intellij.plugin.maven.configuration

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
    name = "PackageSearchMavenConfiguration",
    storages = [(Storage(PackageSearchGeneralConfiguration.StorageFileName))],
)
internal class PackageSearchMavenConfiguration : PersistentStateComponent<PackageSearchMavenConfiguration.State> {

    companion object {

        const val DEFAULT_MAVEN_SCOPE = "compile"

        @JvmStatic
        fun getInstance(project: Project) = project.service<PackageSearchMavenConfiguration>()
    }

    var defaultMavenScope: String = DEFAULT_MAVEN_SCOPE

    override fun getState(): State = State().also {
        it.defaultMavenScope = defaultMavenScope
    }

    override fun loadState(state: State) {
        defaultMavenScope = state.defaultMavenScope.nullize(true) ?: DEFAULT_MAVEN_SCOPE
    }

    fun getMavenScopes() = listOf(defaultMavenScope, "provided", "runtime", "test", "system", "import")

    class State : BaseState() {

        @get:OptionTag("MAVEN_SCOPES_DEFAULT")
        var defaultMavenScope by string(DEFAULT_MAVEN_SCOPE)
    }
}
