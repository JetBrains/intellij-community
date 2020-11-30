package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.left

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsActions.ActionText
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchToolWindowModel
import org.jetbrains.annotations.Nls
import javax.swing.JLabel

class ModuleContextComboBox(viewModel: PackageSearchToolWindowModel) : ContextComboBoxBase(viewModel) {

    override fun createNameLabel() = JLabel("")
    override fun createValueLabel() = object : JLabel() {
        override fun getIcon() = viewModel.selectedProjectModule.value?.moduleType?.icon
            ?: AllIcons.General.ProjectStructure

        override fun getText() = viewModel.selectedProjectModule.value?.name
            ?: PackageSearchBundle.message("packagesearch.ui.toolwindow.allModules")
    }

    override fun createActionGroup(): ActionGroup {
        return DefaultActionGroup(
            createSelectProjectAction(),
            DefaultActionGroup(createSelectModuleActions())
        )
    }

    private fun createSelectProjectAction() = createSelectAction(null, PackageSearchBundle.message("packagesearch.ui.toolwindow.allModules"))

    private fun createSelectModuleActions(): List<AnAction> =
        viewModel.projectModules.value
            .sortedBy { it.getFullName() }
            .map {
                createSelectAction(it, it.getFullName())
            }

    private fun createSelectAction(projectModule: ProjectModule?, @ActionText title: String) =
        object : AnAction(title, title, projectModule?.moduleType?.icon ?: AllIcons.General.ProjectStructure) {
            override fun actionPerformed(e: AnActionEvent) {
                viewModel.selectedProjectModule.set(projectModule)
                updateLabel()
            }
        }
}
