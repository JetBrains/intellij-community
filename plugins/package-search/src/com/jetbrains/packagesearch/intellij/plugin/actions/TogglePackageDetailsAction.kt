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
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration

class TogglePackageDetailsAction(
    private val project: Project,
    private val selectedCallback: (Boolean) -> Unit
) : ToggleAction(
    PackageSearchBundle.message("packagesearch.actions.showDetails.text"),
    PackageSearchBundle.message("packagesearch.actions.showDetails.description"),
    AllIcons.Actions.PreviewDetails
) {

    override fun isSelected(e: AnActionEvent) = PackageSearchGeneralConfiguration.getInstance(project).packageDetailsVisible

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        PackageSearchGeneralConfiguration.getInstance(project).packageDetailsVisible = state
        selectedCallback.invoke(state)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
