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

package com.jetbrains.packagesearch.intellij.plugin.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.configuration.ui.PackageSearchGeneralConfigurable

@Suppress("DialogTitleCapitalization")
class ShowSettingsAction(private val currentProject: Project? = null) :
    AnAction(
        PackageSearchBundle.message("packagesearch.actions.showSettings.text"),
        PackageSearchBundle.message("packagesearch.actions.showSettings.description"),
        AllIcons.General.Settings
    ) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: currentProject ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, PackageSearchGeneralConfigurable::class.java)
    }
}
