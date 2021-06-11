package com.jetbrains.packagesearch.intellij.plugin.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.fus.FUSGroupIds
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger

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
        PackageSearchEventsLogger.logToggle(FUSGroupIds.ToggleTypes.PackageDetails, state)
    }
}
