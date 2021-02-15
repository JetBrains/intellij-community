package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.left

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.util.NlsActions.ActionText
import com.intellij.ui.SizedIcon
import com.intellij.util.PlatformIcons
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.api.model.V2Repository
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchToolWindowModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.RepositoryColorManager
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.localizedName
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.JLabel

class RepositoryContextComboBox(viewModel: PackageSearchToolWindowModel) : ContextComboBoxBase(viewModel) {

    private val emptyIcon = EmptyIcon.create(JBUI.scale(15)) // see PopupFactoryImpl.calcMaxIconSize

    override fun createNameLabel() = JLabel("")
    override fun createValueLabel() = object : JLabel() {
        override fun getText() = viewModel.selectedRemoteRepository.value?.localizedName()
            ?: PackageSearchBundle.message("packagesearch.ui.toolwindow.allRepositories")
    }

    override fun createActionGroup(): ActionGroup {
        return DefaultActionGroup(
            createSelectAllRepositoriesAction(),
            Separator(),
            DefaultActionGroup(createSelectRepositoryActions())
        )
    }

    private fun createSelectAllRepositoriesAction() =
        createSelectAction(null, PackageSearchBundle.message("packagesearch.ui.toolwindow.allRepositories"))

    private fun createSelectRepositoryActions(): List<AnAction> =
        viewModel.repositories.value
            .mapNotNull { it.remoteInfo }
            .distinct()
            .sortedBy { it.localizedName() }
            .map {
                createSelectAction(it, it.localizedName())
            }

    private fun createSelectAction(repository: V2Repository?, @ActionText title: String) =
        object : ToggleAction(title, null, createIcon(repository)) {
            fun isSelected() = viewModel.selectedRemoteRepository.value == repository

            override fun update(e: AnActionEvent) {
                val icon = e.presentation.icon
                if (icon is RepositoryColorIcon) {
                    icon.prepare(isSelected())
                }
                super.update(e)
            }

            override fun isSelected(e: AnActionEvent): Boolean = isSelected()

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                viewModel.selectedRemoteRepository.set(repository)
                updateLabel()
            }
        }

    private fun createIcon(repository: V2Repository?) =
        if (repository == null) {
            emptyIcon
        } else {
            RepositoryColorIcon(
                emptyIcon.iconWidth,
                RepositoryColorManager.getBackgroundColor(
                    viewModel.repositoryColorManager.getColor(repository)))
        }
}

class RepositoryColorIcon(size: Int, color: Color) : ColorIcon(size, color) {

    private var isSelected = false
    private val sizedIcon = SizedIcon(PlatformIcons.CHECK_ICON_SMALL, size, size)

    fun prepare(selected: Boolean) {
        isSelected = selected
    }

    override fun paintIcon(component: Component, g: Graphics, x: Int, y: Int) {
        super.paintIcon(component, g, x, y)
        if (isSelected) {
            sizedIcon.paintIcon(component, g, x, y)
        }
    }
}
