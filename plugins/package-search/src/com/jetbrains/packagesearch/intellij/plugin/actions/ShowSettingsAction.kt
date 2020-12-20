package com.jetbrains.packagesearch.intellij.plugin.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.configuration.ui.PackageSearchGeneralConfigurable

class ShowSettingsAction(private val currentProject: Project? = null) :
    AnAction(
        PackageSearchBundle.message("packagesearch.actions.showSettings.text"),
        PackageSearchBundle.message("packagesearch.actions.showSettings.description"),
        AllIcons.General.Settings) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: currentProject ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, PackageSearchGeneralConfigurable::class.java)
    }
}
