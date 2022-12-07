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

package com.jetbrains.packagesearch.intellij.plugin.maven

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import com.jetbrains.packagesearch.intellij.plugin.maven.configuration.PackageSearchMavenConfiguration
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import icons.OpenapiIcons
import javax.swing.Icon

internal object MavenProjectModuleType : ProjectModuleType {

    override val icon: Icon
        get() = OpenapiIcons.RepositoryLibraryLogo

    override val packageIcon: Icon
        get() = icon

    override fun defaultScope(project: Project): PackageScope =
        PackageSearchMavenConfiguration.getInstance(project).determineDefaultMavenScope()
            .let { if (it == "compile") PackageScope.Missing else PackageScope.from(it) }

    override fun userDefinedScopes(project: Project): List<PackageScope> =
        PackageSearchMavenConfiguration.getInstance(project).getMavenScopes()
            .map { PackageScope.from(it) }
}
