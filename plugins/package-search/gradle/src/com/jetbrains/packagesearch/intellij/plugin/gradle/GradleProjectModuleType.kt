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

package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import com.jetbrains.packagesearch.intellij.plugin.gradle.configuration.PackageSearchGradleConfiguration
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import icons.GradleIcons
import javax.swing.Icon

internal object GradleProjectModuleType : ProjectModuleType {

    override val icon: Icon
        get() = GradleIcons.Gradle // TODO use KotlinIcons.MPP if it's a K/MP module

    override val packageIcon: Icon
        get() = GradleIcons.GradleFile // TODO use KotlinIcons.MPP if it's a K/MP module

    override fun defaultScope(project: Project): PackageScope =
        PackageScope.from(PackageSearchGradleConfiguration.getInstance(project).determineDefaultGradleScope())

    override fun userDefinedScopes(project: Project): List<PackageScope> =
        PackageSearchGradleConfiguration.getInstance(project).getGradleScopes()
            .map { PackageScope.from(it) }
}
