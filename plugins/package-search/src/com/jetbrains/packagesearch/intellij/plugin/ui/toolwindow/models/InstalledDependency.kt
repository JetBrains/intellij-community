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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.buildsystem.model.unified.UnifiedDependency
import org.jetbrains.packagesearch.api.v2.ApiStandardPackage

internal data class InstalledDependency(val groupId: String, val artifactId: String) {

    val coordinatesString
        get() = "$groupId:$artifactId"

    companion object {

        fun from(dependency: UnifiedDependency): InstalledDependency? {
            val groupId = dependency.coordinates.groupId
            val artifactId = dependency.coordinates.artifactId
            if (groupId == null || artifactId == null) return null

            return InstalledDependency(groupId, artifactId)
        }

        fun from(standardV2Package: ApiStandardPackage) = InstalledDependency(standardV2Package.groupId, standardV2Package.artifactId)
    }
}

internal fun DeclaredDependency.asInstalledDependencyOrNull(): InstalledDependency? {
    return InstalledDependency(
        coordinates.groupId ?: return null,
        coordinates.artifactId ?: return null
    )
}

internal fun DeclaredDependency.asInstalledDependency() = asInstalledDependencyOrNull()
    ?: error("$this has either groupId or artifactId set to null")